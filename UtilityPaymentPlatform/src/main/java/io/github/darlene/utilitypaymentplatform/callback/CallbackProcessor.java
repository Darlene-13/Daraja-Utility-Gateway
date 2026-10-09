package io.github.darlene.utilitypaymentplatform.callback;

import io.github.darlene.utilitypaymentplatform.common.OutboxEvent;
import io.github.darlene.utilitypaymentplatform.common.OutboxEventRepository;
import io.github.darlene.utilitypaymentplatform.callback.infrastructure.CallBackLogRepository;
import io.github.darlene.utilitypaymentplatform.payment.domain.Status;
import io.github.darlene.utilitypaymentplatform.payment.domain.Transaction;
import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackProcessor {

    private final CallBackLogRepository callbackLogRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void process(CallbackPayload payload, String rawJson) {

        // saveAndFlush sends the insert now, so a duplicate fails here
        // and rolls everything back, instead of failing later at commit
        CallbackLog callbackLog = new CallbackLog();
        callbackLog.setCheckoutRequestId(payload.checkoutRequestId());
        callbackLog.setRawPayload(rawJson);
        callbackLogRepository.saveAndFlush(callbackLog);

        Optional<Transaction> found =
                transactionRepository.findByCheckoutRequestId(payload.checkoutRequestId());

        if (found.isEmpty()) {
            log.warn("Callback for unknown checkoutRequestId {}", payload.checkoutRequestId());
            return;
        }

        Transaction transaction = found.get();

        if (transaction.getStatus() != Status.PENDING_CONFIRMATION) {
            log.info("Transaction {} already resolved, skipping", transaction.getId());
            return;
        }

        if (payload.success()) {
            transaction.setStatus(Status.PAID);
            transaction.setMpesaReceiptNumber(payload.mpesaReceiptNumber());
        } else {
            transaction.setStatus(Status.FAILED);
            transaction.setFailureReason(payload.errorMessage());
        }
        transactionRepository.save(transaction);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("TRANSACTION");
        event.setAggregateId(transaction.getId());
        event.setEventType(payload.success() ? "PAYMENT_CONFIRMED" : "PAYMENT_FAILED");
        event.setPayload(String.format(
                "{\"transactionId\":\"%s\",\"status\":\"%s\"}",
                transaction.getId(), transaction.getStatus()));
        outboxEventRepository.save(event);
    }
}