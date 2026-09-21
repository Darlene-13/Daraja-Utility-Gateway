package io.github.darlene.utilitypaymentplatform.callback;


import io.github.darlene.utilitypaymentplatform.callback.infrastructure.CallBacklogRepository;
import io.github.darlene.utilitypaymentplatform.common.OutboxEventRepository;
import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import io.lettuce.core.StreamMessage;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@AllArgsConstructor
public class CallBackWorker {

    private final OutboxEventRepository outboxEventRepository;  // Writes the outbox row from common/outbox
    private final TransactionRepository transactionRepository; //Find by checkout request id to locate the right transaction
    private final CallBacklogRepository callBacklogRepository;  // Dedupes through save and catch
    private final StringRedisTemplate redisTemplate;  // Reads from the stream published by callback ingest publisher
    private final String streamName;   // callback - ingest
    private final String consumerGroupName; // identifies the worker as part of the shared group.
    private final ObjectMapper objectMapper;

    //Save dedupe and write outbox needs to happen as one db transaction: ATOMICITY.
//    //On message does not to db transactional checks it does stream mechanics: receiving, parsing, acknowledging and delegating to the db part
//    public void onMessage(StreamMessage<String, String> streamMessage) {
//
//        String rawJson = streamMessage.getValue().get("payload");
//
//        CallbackPayload payload = parsePayload(rawJson);
//
//        processCallback(payload);
//
//        acknowledge(streamMessage.getId());
//
//
//    }
//
//    private void acknowledge(String id) {
//    }
//
//    private void processCallback(CallbackPayload payload) {
//    }
//
//    private CallbackPayload parsePayload(String rawJson) {
//    }

}