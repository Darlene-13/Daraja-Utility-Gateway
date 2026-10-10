package io.github.darlene.utilitypaymentplatform.common;

import io.github.darlene.utilitypaymentplatform.meter.Meter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/*
 * Spring Boot only autoconfigures StringRedisTemplate and a raw RedisTemplate<Object, Object>.
 * The meter cache needs a typed template, so we define it here.
 */
@Configuration
public class RedisConfig {

    // Keys are plain strings ("meter:<number>"), values are Meter stored as JSON
    @Bean
    public RedisTemplate<String, Meter> meterRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Meter> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(Meter.class));

        template.afterPropertiesSet();
        return template;
    }
}
