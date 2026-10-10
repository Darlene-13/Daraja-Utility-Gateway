package io.github.darlene.utilitypaymentplatform.token;

import io.github.darlene.utilitypaymentplatform.token.worker.TokenWorker;
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
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamReadRequest;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@Configuration
@EnableScheduling
public class TokenGroupConfig {

    @Value("${token.stream-name}")
    private String streamName;

    @Value("${token.consumer-group}")
    private String consumerGroupName;

    @Value("${token.consumer-name}")
    private String consumerName;

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> tokenGroupContainer(
            RedisConnectionFactory factory,
            StringRedisTemplate redisTemplate,
            TokenWorker worker) {

        createGroupIfMissing(redisTemplate);

        var options = StreamMessageListenerContainerOptions.builder()
                .batchSize(10)
                .pollTimeout(Duration.ofSeconds(2))
                .build();

        var container = StreamMessageListenerContainer.create(factory, options);

        var request = StreamReadRequest
                .builder(StreamOffset.create(streamName, ReadOffset.lastConsumed()))
                .consumer(Consumer.from(consumerGroupName, consumerName))
                .autoAcknowledge(false)
                .cancelOnError(t -> false)
                .build();

        container.register(request, worker);
        return container;
    }

    private void createGroupIfMissing(StringRedisTemplate redisTemplate) {
        try {
            redisTemplate.execute((RedisCallback<Object>) conn ->
                    conn.streamCommands().xGroupCreate(
                            streamName.getBytes(), consumerGroupName, ReadOffset.from("0"), true));
        } catch (RedisSystemException e) {
            boolean busy = String.valueOf(e.getMessage()).contains("BUSYGROUP")
                    || (e.getCause() != null && String.valueOf(e.getCause().getMessage()).contains("BUSYGROUP"));
            if (!busy) throw e;
        }
    }
}