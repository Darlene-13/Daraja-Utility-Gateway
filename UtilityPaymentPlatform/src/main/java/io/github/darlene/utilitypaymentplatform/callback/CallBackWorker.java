package io.github.darlene.utilitypaymentplatform.callback;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Slf4j
@Component
public class CallBackWorker implements StreamListener<String, MapRecord<String, String, String>>{

    private final StringRedisTemplate redisTemplate;  // Reads from the stream published by callback ingest publisher
    private final CallbackProcessor callbackProcessor;

    @Value("${callback.stream-name}")
    private final String streamName;

    @Value("${callback.consumer-group}")
    private final String consumerGroupName;

    // needed to claim pending messages in reclaimStuck()
    @Value("${callback.consumer-name}")
    private final String consumerName;

    @Value("${callback.reclaim-idle-ms}")
    private final long reclaimIdleMs;

    @Value("${callback.max-attempts}")
    private final long maxAttempts;

    @Value("${callback.dead-letter-stream}")
    private final String deadLetterStream;

    private final ObjectMapper objectMapper;

    //Save dedupe and write outbox needs to happen as one db transaction: ATOMICITY.
    //On message does not to db transactional checks it does stream mechanics: receiving, parsing, acknowledging and delegating to the db part

    // Redis streaming offers more data persistence as compared to Redis pub/sub
    //Map record is basically one Redis stream entry
    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        handle(record.getId(), record.getValue());
    }

    // The listener only reads new messages, so one that failed (DB down, crash before the ack)
    // sits in the pending list forever unless something claims it. This retries the ones idle
    // for reclaimIdleMs and parks them once they have been delivered maxAttempts times.
    @Scheduled(fixedDelayString = "${callback.reclaim-idle-ms}")
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
                    log.error("Callback {} failed {} times, parking it",
                            record.getId(), message.getTotalDeliveryCount());
                    park(record.getId(), fields);
                } else {
                    handle(record.getId(), fields);
                }
            }
        }
    }

    private void handle(RecordId id, Map<String, String> fields){
        String rawJson = fields.get("payload");

        CallbackPayload payload;
        try {
            payload = parsePayload(rawJson);
        } catch (Exception bad) {
            // a malformed callback will never parse, so don't retry it
            log.error("Unreadable callback {}, parking it", id, bad);
            park(id, fields);
            return;
        }

        try{
            callbackProcessor.process(payload, rawJson);
            acknowledge(id);
        } catch (DataIntegrityViolationException duplicate){
            // also covers a crash between the DB commit and the ack: the retry hits the unique key
            log.info("Duplicate callback {}, already handled", id);
            acknowledge(id);

        } catch (Exception e){
            // no ack: stays pending and reclaimStuck() retries it
            log.error("Callback {} failed, leaving it pending", id, e);
        }
    }
    //Why do we have to acknowledge
    //Redis guarantees message delivery therefore we need to acknowledge after a message is processed so that
    // Redis can know and take the next message from the stream, and it won't be able to share entries
    // to other consumers?
    //XACK messages group 1526984818136-0
    private void acknowledge(RecordId id) {
        redisTemplate.opsForStream().acknowledge(streamName, consumerGroupName, id);
    }

    // Dead-letter stream: kept for a person to look at, out of the retry loop
    private void park(RecordId id, Map<String, String> fields) {
        redisTemplate.opsForStream().add(deadLetterStream, fields);
        acknowledge(id);
    }

    private Map<String, String> toStringMap(Map<Object, Object> raw) {
        Map<String, String> fields = new HashMap<>();
        raw.forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
        return fields;
    }

    private CallbackPayload parsePayload(String rawJson) {
        JsonNode stkCallback = objectMapper.readTree(rawJson).path("Body").path("stkCallback");
        String checkoutRequestId = stkCallback.path("CheckoutRequestID").asText();
        if(checkoutRequestId == null || checkoutRequestId.isBlank()){
            throw new IllegalArgumentException("Callback has no checkout request ID");
        }

        boolean success = stkCallback.path("ResultCode").asInt(-1) == 0;
        String errorMessage = success ? null :stkCallback.path("ResultDesc").asText();
        String receipt = null;
        if (success){
            for (JsonNode item: stkCallback.path("CallbackMetadata").path("Item")){
                if ("MpesaReceiptNumber".equals(item.path("Name").asText())) {
                    receipt = item.path("Value").asText();
                    break;
                }
            }
        }
        return new CallbackPayload(checkoutRequestId, success, errorMessage, receipt);
    }
}