package io.github.darlene.utilitypaymentplatform.callback;

import io.github.darlene.utilitypaymentplatform.common.OutboxEvent;
import io.github.darlene.utilitypaymentplatform.common.OutboxEventRepository;
import io.github.darlene.utilitypaymentplatform.callback.infrastructure.CallBackLogRepository;
import io.github.darlene.utilitypaymentplatform.payment.domain.Status;
import io.github.darlene.utilitypaymentplatform.payment.domain.Transaction;
import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import io.github.darlene.utilitypaymentplatform.token.model.PaymentEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackProcessor {

    private final CallBackLogRepository callbackLogRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(CallbackPayload payload, String rawJson) {

        // saveAndFlush sends the insert now, so a duplicate fails here
        // and rolls everything back, instead of failing later at commit
        CallbackLog callbackLog = new CallbackLog();
        callbackLog.setCheckoutRequestId(payload.checkoutRequestId());
        callbackLog.setRawPayload(rawJson);
        callbackLogRepository.saveAndFlush(callbackLog);

        resolve(payload);
    }

    // Moves a PENDING_CONFIRMATION transaction to PAID or FAILED and writes the outbox event.
    // Shared by the callback path and ReconciliationJob, so whichever learns the result first
    // wins: the other sees the transaction already resolved, and @Version on Transaction
    // makes one of them fail if both commit at the same moment.
    @Transactional
    public void resolve(CallbackPayload payload) {
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
        event.setPayload(payload.success() ? confirmedPayload(transaction) : failedPayload(transaction));
        outboxEventRepository.save(event);
    }

    // Carries everything the token worker needs to vend, so it never has to read
    // the payment tables. Meter is lazy but we are still inside the transaction.
    private String confirmedPayload(Transaction transaction) {
        return objectMapper.writeValueAsString(new PaymentEventPayload(
                transaction.getId(),
                transaction.getCheckoutRequestId(),
                transaction.getMeter().getMeterNumber(),
                transaction.getMeter().getMeterType(),
                transaction.getAmount()));
    }

    // LinkedHashMap because failureReason can be null and Map.of rejects nulls
    private String failedPayload(Transaction transaction) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionId", transaction.getId());
        body.put("checkoutRequestId", transaction.getCheckoutRequestId());
        body.put("failureReason", transaction.getFailureReason());
        return objectMapper.writeValueAsString(body);
    }
}