package io.github.darlene.utilitypaymentplatform.reconciliation;

import io.github.darlene.utilitypaymentplatform.callback.CallbackPayload;
import io.github.darlene.utilitypaymentplatform.callback.CallbackProcessor;
import io.github.darlene.utilitypaymentplatform.payment.domain.Status;
import io.github.darlene.utilitypaymentplatform.payment.domain.StkQueryResult;
import io.github.darlene.utilitypaymentplatform.payment.domain.Transaction;
import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja.DarajaStkQueryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

// Safety net for callbacks that never reached us. Safaricom sends each callback once, so if
// it is lost (Redis down, app down, network) the transaction would sit in PENDING_CONFIRMATION
// forever: maybe paid with no token, maybe failed with nobody told. This asks Daraja directly.
@Slf4j
@Component
public class ReconciliationJob {

    private final TransactionRepository transactionRepository;
    private final DarajaStkQueryClient stkQueryClient;
    private final CallbackProcessor callbackProcessor;
    private final TransactionTemplate txTemplate;
    private final Duration pendingAfter;
    private final Duration giveUpAfter;

    public ReconciliationJob(TransactionRepository transactionRepository,
                             DarajaStkQueryClient stkQueryClient,
                             CallbackProcessor callbackProcessor,
                             TransactionTemplate txTemplate,
                             @Value("${reconciliation.pending-after-ms}") long pendingAfterMs,
                             @Value("${reconciliation.give-up-after-ms}") long giveUpAfterMs) {
        this.transactionRepository = transactionRepository;
        this.stkQueryClient = stkQueryClient;
        this.callbackProcessor = callbackProcessor;
        this.txTemplate = txTemplate;
        this.pendingAfter = Duration.ofMillis(pendingAfterMs);
        this.giveUpAfter = Duration.ofMillis(giveUpAfterMs);
    }

    @Scheduled(fixedDelayString = "${reconciliation.interval-ms}")
    public void reconcile() {
        // pendingAfter gives the real callback a fair chance to arrive first
        OffsetDateTime cutoff = OffsetDateTime.now().minus(pendingAfter);
        List<Transaction> overdue = transactionRepository
                .findTop50ByStatusAndNeedsReviewFalseAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        Status.PENDING_CONFIRMATION, cutoff);

        for (Transaction tx : overdue) {
            try {
                reconcile(tx);
            } catch (Exception e) {
                // includes losing the optimistic lock to a callback that arrived meanwhile;
                // one bad row should not stop the rest of the batch
                log.error("Reconciling transaction {} failed, trying again next round", tx.getId(), e);
            }
        }
    }

    private void reconcile(Transaction tx) {
        StkQueryResult result = stkQueryClient.query(tx.getCheckoutRequestId());

        switch (result.state()) {
            // same path as a real callback: status change and outbox event in one DB transaction.
            // The query does not return the M-Pesa receipt, so it stays null.
            case PAID -> {
                log.info("Reconciled {} as paid", tx.getCheckoutRequestId());
                callbackProcessor.resolve(new CallbackPayload(
                        tx.getCheckoutRequestId(), true, null, null));
            }
            case FAILED -> {
                log.info("Reconciled {} as failed: {}", tx.getCheckoutRequestId(), result.description());
                callbackProcessor.resolve(new CallbackPayload(
                        tx.getCheckoutRequestId(), false, result.description(), null));
            }
            case STILL_PROCESSING, UNKNOWN -> {
                // leave it for the next round, unless it has been stuck too long
                if (tx.getCreatedAt().isBefore(OffsetDateTime.now().minus(giveUpAfter))) {
                    flagForReview(tx.getCheckoutRequestId(),
                            "Unresolved after " + giveUpAfter + ": " + result.description());
                }
            }
        }
    }

    // We still don't know if the customer paid, so the status stays PENDING_CONFIRMATION
    // and a person checks the M-Pesa portal. needsReview also stops this job picking it up.
    private void flagForReview(String checkoutRequestId, String reason) {
        txTemplate.executeWithoutResult(status ->
                transactionRepository.findByCheckoutRequestId(checkoutRequestId)
                        .filter(tx -> tx.getStatus() == Status.PENDING_CONFIRMATION)
                        .ifPresent(tx -> {
                            log.error("Transaction {} could not be reconciled, flagging for review", tx.getId());
                            tx.setNeedsReview(true);
                            tx.setFailureReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
                            transactionRepository.save(tx);
                        }));
    }
}
