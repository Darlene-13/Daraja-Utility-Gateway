package io.github.darlene.utilitypaymentplatform.payment.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * The rate limiters are plain classes that take their limits through the constructor,
 * so Spring can't create them on its own. We build them here with values from application.properties.
 */
@Configuration
public class RateLimiterConfig {

    // Limits payment attempts per phone number
    @Bean
    public SlidingWindowRateLimiter paymentRateLimiter(
            @Value("${rate-limit.payment.limit}") long limit,
            @Value("${rate-limit.payment.window-ms}") long windowSizeMs) {
        return new SlidingWindowRateLimiter(limit, windowSizeMs);
    }
}
