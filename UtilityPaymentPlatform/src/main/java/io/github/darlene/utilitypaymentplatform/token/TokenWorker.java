package io.github.darlene.utilitypaymentplatform.token;


import io.github.darlene.utilitypaymentplatform.common.OutboxEventRepository;
import io.github.darlene.utilitypaymentplatform.payment.domain.TransactionRepository;
import io.github.darlene.utilitypaymentplatform.token.tokenissuer.TokenIssuer;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@AllArgsConstructor
@Component
public class TokenWorker implements StreamListener<String, MapRecord<String,String, String>> {

    private final ObjectMapper objectMapper;
    private final TokenIssuer tokenIssuer;
    private final TransactionRepository transactionRepository; //To load the transaction the event refers to
    private final OutboxEventRepository outboxEventRepository; // To write token issued event once done.
    private final StringRedisTemplate redisTemplate;

    @Value("${token.stream-name}")
    private final String streamName;

    @Value("${token.consumer-group}")
    private final String consumerGroupName;

    @Value("${token.consumer-name}")
    private final String consumerName;



    @Override
    public void onMessage(MapRecord<String, String, String> streamMessage) {
        String rawJson = streamMessage.getValue().get("payload");
        parsePayload(rawJson);

        processPaymentEvent(payload);



    }

    private void parsePayload(String rawJson) {
        objectMapper.readValue(rawJson, PaymentEventPayload.class);
    }
}