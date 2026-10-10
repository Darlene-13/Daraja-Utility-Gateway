package io.github.darlene.utilitypaymentplatform.token.worker;


import io.github.darlene.utilitypaymentplatform.token.model.PaymentEventPayload;
import io.github.darlene.utilitypaymentplatform.token.service.TokenProcessor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@Component
public class TokenWorker implements StreamListener<String, MapRecord<String,String, String>> {

    // OutboxPublisher only routes PAYMENT_CONFIRMED here, the check in handle() is a guard
    private static final String PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";

    private final ObjectMapper objectMapper;
    private final TokenProcessor tokenProcessor;
    private final StringRedisTemplate redisTemplate;

    @Value("${token.stream-name}")
    private final String streamName;

    @Value("${token.consumer-group}")
    private final String consumerGroupName;

    @Value("${token.consumer-name}")
    private final String consumerName;

    @Value("${token.reclaim-idle-ms}")
    private final long reclaimIdleMs;

    @Value("${token.max-attempts}")
    private final long maxAttempts;

    @Value("${token.dead-letter-stream}")
    private final String deadLetterStream;


    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        handle(record.getId(), record.getValue());
    }

    // Failed messages stay pending in the group. This picks up the ones idle for reclaimIdleMs
    // and retries them, or gives up once they have been delivered maxAttempts times.
    @Scheduled(fixedDelayString = "${token.reclaim-idle-ms}")
    public void reclaimStuck() {
        PendingMessages pending = redisTemplate.opsForStream()
                .pending(streamName, consumerGroupName, Range.unbounded(), 50);

        for (PendingMessage message : pending) {
            if (message.getElapsedTimeSinceLastDelivery().toMillis() < reclaimIdleMs) {
                continue;
            }

            // claim checks the idle time again, so if another consumer took it meanwhile we get nothing back
            List<MapRecord<String, Object, Object>> claimed = redisTemplate.opsForStream().claim(
                    streamName, consumerGroupName, consumerName,
                    Duration.ofMillis(reclaimIdleMs), message.getId());

            for (MapRecord<String, Object, Object> record : claimed) {
                Map<String, String> fields = toStringMap(record.getValue());
                if (message.getTotalDeliveryCount() >= maxAttempts) {
                    giveUp(record.getId(), fields,
                            "Token issuing failed after " + message.getTotalDeliveryCount() + " attempts");
                } else {
                    handle(record.getId(), fields);
                }
            }
        }
    }
    // eventType and payload are the fields OutboxPublisher writes on every stream message
    private void handle(RecordId id, Map<String, String> fields) {
        String eventType = fields.get("eventType");
        if (!PAYMENT_CONFIRMED.equals(eventType)) {
            log.debug("Skipping {} event {}", eventType, id);
            acknowledge(id);
            return;
        }

        PaymentEventPayload payload;
        try {
            payload = parsePayload(fields.get("payload"));
        } catch (IllegalArgumentException bad) {
            // a malformed message will never succeed, so don't retry it
            log.error("Unreadable payment event {}, parking it", id, bad);
            park(id, fields);
            return;
        }

        try {
            tokenProcessor.process(payload);
            acknowledge(id);              // only after the DB write committed
        } catch (IllegalStateException permanent) {
            // no issuer for the meter type or no matching transaction, a retry gives the same answer
            log.error("Token issuing for {} cannot succeed, giving up", id, permanent);
            giveUp(id, fields, permanent.getMessage());
        } catch (Exception e) {
            // no ack: stays pending and reclaimStuck() retries it
            log.error("Token issuing failed for {}, leaving pending", id, e);
        }
    }

    // Marks the transaction FAILED_TOKEN_ISSUANCE, then parks and acks so the message stops retrying
    private void giveUp(RecordId id, Map<String, String> fields, String reason) {
        PaymentEventPayload payload;
        try {
            payload = parsePayload(fields.get("payload"));
        } catch (IllegalArgumentException bad) {
            park(id, fields);
            return;
        }

        try {
            tokenProcessor.markIssuanceFailed(payload.checkoutRequestId(), reason);
        } catch (Exception e) {
            // still pending, the next reclaimStuck() round tries to give up again
            log.error("Could not record token failure for {}, leaving pending", id, e);
            return;
        }
        park(id, fields);
    }

    private PaymentEventPayload parsePayload(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, PaymentEventPayload.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad payment event payload", e);
        }
    }

    private void acknowledge(RecordId id) {
        redisTemplate.opsForStream().acknowledge(streamName, consumerGroupName, id);
    }

    private void park(RecordId id, Map<String, String> fields) {
        redisTemplate.opsForStream().add(deadLetterStream, fields);
        acknowledge(id);
    }

    private Map<String, String> toStringMap(Map<Object, Object> raw) {
        Map<String, String> fields = new HashMap<>();
        raw.forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        return fields;
    }

}
