package io.github.darlene.utilitypaymentplatform.callback;

import io.github.darlene.utilitypaymentplatform.token.TokenWorker;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;

@Configuration
@AllArgsConstructor
public class CallbackConsumerGroupConfig {


    @Value("${token.stream-name}")
    private final String streamName;

    @Value("${token.consumer-group}")
    private final String consumerGroupName;

    @Value("${token.consumer-name}")
    private final String consumerName;

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> tokenGroupContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            TokenWorker tokenWorker
    ){
        //Create a group if mission
        createdGroupIfMissing(redisTemplate);

        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .batchSize(10)
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        var container = StreamMessageListenerContainer.create(connectionFactory, options);

        var request = StreamMessageListenerContainer.StreamReadRequest
                .builder(StreamOffset.create(streamName, ReadOffset.lastConsumed()))
                .consumer(Consumer.from(consumerGroupName, consumerName))
                .autoAcknowledge(false)    //We acknowledge manually after the DB commit.
                .cancelOnError(t -> false)
                .build();

        container.register(request, tokenWorker);
        return container;

    }

    private void createdGroupIfMissing(StringRedisTemplate redisTemplate) {

        try {
            redisTemplate.execute((RedisCallback<Object>) conn ->
                    conn.streamCommands().xGroupCreate(
                            streamName.getBytes(), consumerGroupName, ReadOffset.from("0"), true)
                    );
        } catch (RedisSystemException e){
            boolean busy = String.valueOf(e.getMessage()).contains("BUSY GROUP")
                    || (e.getCause() != null && String.valueOf(e.getCause().getMessage()).contains("BUSYGROUP"));

            if(!busy) throw e;  //Busy group means that the group already exists.
        }

    }
}
