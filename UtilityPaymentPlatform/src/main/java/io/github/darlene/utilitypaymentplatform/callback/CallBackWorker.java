package io.github.darlene.utilitypaymentplatform.callback;


import io.github.darlene.utilitypaymentplatform.callback.infrastructure.CallBackLogRepository;
import io.github.darlene.utilitypaymentplatform.common.OutboxEventRepository;
import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AllArgsConstructor
@Slf4j
public class CallBackWorker implements StreamListener<String, MapRecord<String, String, String>>{

    private final StringRedisTemplate redisTemplate;  // Reads from the stream published by callback ingest publisher
    private final CallbackProcessor callbackProcessor;

    @Value("${token.stream-name}")
    private final String streamName;

    @Value("${token.consumer-group}")
    private final String consumerGroupName;


    private final ObjectMapper objectMapper;

    //Save dedupe and write outbox needs to happen as one db transaction: ATOMICITY.
    //On message does not to db transactional checks it does stream mechanics: receiving, parsing, acknowledging and delegating to the db part

    // Redis streaming offers more data persistence as compared to Redis pub/sub
    //Map record is basically one Redis stream entry
    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        handle(record);
    }

    private void handle(MapRecord<String, String, String> streamMessage){
        String rawJson = streamMessage.getValue().get("payload");
        try{
            CallbackPayload payload = parsePayload(rawJson);
            callbackProcessor.process(payload, rawJson);
            acknowledge(streamMessage.getId());
        } catch (DataIntegrityViolationException duplicate){
            log.info("Duplicate callback {}, already handled", streamMessage.getId());
            acknowledge(streamMessage.getId());

        } catch (Exception e){
            log.error("Callback {} failed, leaving it pending", streamMessage.getId(), e);
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