package io.github.darlene.utilitypaymentplatform.token.service;

import io.github.darlene.utilitypaymentplatform.common.OutboxEvent;
import io.github.darlene.utilitypaymentplatform.common.OutboxEventRepository;
import io.github.darlene.utilitypaymentplatform.meter.MeterType;
import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import io.github.darlene.utilitypaymentplatform.token.TokenResult;
import io.github.darlene.utilitypaymentplatform.token.model.PaymentEventPayload;
import io.github.darlene.utilitypaymentplatform.token.tokenissuer.TokenIssuer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class TokenProcessor {

    private final Map<MeterType, TokenIssuer> issuers;
    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    // Spring can only inject a Map keyed by bean name, so we take every
    // TokenIssuer bean as a list and key them by the meter type they handle
    public TokenProcessor(List<TokenIssuer> issuerList,
                          TransactionRepository transactionRepository,
                          OutboxEventRepository outboxEventRepository,
                          TransactionTemplate txTemplate,
                          ObjectMapper objectMapper) {
        this.issuers = new EnumMap<>(MeterType.class);
        for (TokenIssuer issuer : issuerList) {
            TokenIssuer existing = issuers.put(issuer.type(), issuer);
            if (existing != null) {
                throw new IllegalStateException("Two TokenIssuers registered for meter type " + issuer.type());
            }
        }
        this.transactionRepository = transactionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.txTemplate = txTemplate;
        this.objectMapper = objectMapper;
    }

    public void process(PaymentEventPayload payload) {
        // 1. retry safety: a previous attempt may have finished the DB write but crashed before the ack
        if (alreadyIssued(payload.checkoutRequestId())) {
            log.info("Token already issued for {}, skipping", payload.checkoutRequestId());
            return;
        }

        // 2. external call, deliberately outside any DB transaction
        TokenIssuer issuer = resolveIssuer(payload.meterType());
        TokenResult result = issuer.issueToken(
                payload.meterNumber(), payload.amount(), payload.checkoutRequestId());

        // 3. short transaction just for the write
        writeTokenResult(payload, result);
    }

    private boolean alreadyIssued(String checkoutRequestId) {
        return transactionRepository.findByCheckoutRequestId(checkoutRequestId)
                .map(tx -> tx.getToken() != null)
                .orElse(false);
    }

    private TokenIssuer resolveIssuer(MeterType type) {
        TokenIssuer issuer = issuers.get(type);
        if (issuer == null) {
            throw new IllegalStateException("No TokenIssuer registered for meter type " + type);
        }
        return issuer;
    }

    private void writeTokenResult(PaymentEventPayload payload, TokenResult result) {
        txTemplate.executeWithoutResult(status -> {
            var tx = transactionRepository.findByCheckoutRequestId(payload.checkoutRequestId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Transaction not found for " + payload.checkoutRequestId()));

            tx.markTokenIssued(result.token(), result.units(), result.providerReference());
            transactionRepository.save(tx);

            // outbox event so the notification step can send the token to the customer
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("transactionId", tx.getId());
            body.put("token", result.token());
            body.put("units", result.units());
            saveOutboxEvent(tx.getId(), "TOKEN_ISSUED", body);
        });
    }

    // Called once the worker stops retrying. Flags the row for review and tells the
    // notification step, so the customer is not left waiting on a token that won't come.
    public void markIssuanceFailed(String checkoutRequestId, String reason) {
        txTemplate.executeWithoutResult(status -> {
            var found = transactionRepository.findByCheckoutRequestId(checkoutRequestId);
            if (found.isEmpty()) {
                log.warn("No transaction for {}, nothing to mark as failed", checkoutRequestId);
                return;
            }

            var tx = found.get();
            if (tx.getToken() != null) {
                log.info("Transaction {} already has a token, not marking it failed", tx.getId());
                return;
            }

            tx.markTokenIssuanceFailed(reason);
            transactionRepository.save(tx);

            // LinkedHashMap because failureReason can be null and Map.of rejects nulls
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("transactionId", tx.getId());
            body.put("checkoutRequestId", checkoutRequestId);
            body.put("failureReason", tx.getFailureReason());
            saveOutboxEvent(tx.getId(), "TOKEN_ISSUANCE_FAILED", body);
        });
    }

    private void saveOutboxEvent(UUID transactionId, String eventType, Map<String, Object> body) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("TRANSACTION");
        event.setAggregateId(transactionId);
        event.setEventType(eventType);
        event.setPayload(objectMapper.writeValueAsString(body));
        outboxEventRepository.save(event);
    }
}
