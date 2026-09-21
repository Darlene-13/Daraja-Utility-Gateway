package io.github.darlene.utilitypaymentplatform.callback.infrastructure;

import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service
@AllArgsConstructor
public class CallBackIngestPublisher {

    //Once we have Redis spring boot starter we do not have to configure Redis config, spring does that automatically.
    // We use string Redis template because the callback is just a JSON string
    private final StringRedisTemplate redisTemplate;
    private final String streamName;
    // Callback ingest is the stream

    public void publish(String rawPayload){
        Map<String, String> message = Map.of(
                "payload", rawPayload
        );

        redisTemplate.opsForStream().add(streamName, message);
    }

}
