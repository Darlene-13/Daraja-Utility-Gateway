package io.github.darlene.utilitypaymentplatform.payment.infrastructure;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@AllArgsConstructor
public class FixedWindowRateLimiter implements RateLimiter {

    private final long windowSizeMs;
    private final Cache<String, AtomicInteger> cache = Caffeine.newBuilder().expireAfterAccess(Duration.ofMillis(windowSizeMs * 2)).build();
    private final int limit;

    @Override
    public boolean isAllowed(String key){
        String windowKey = currentWindowKey(key);

        AtomicInteger counter = cache.get(windowKey, k-> new AtomicInteger(0));

        int count = counter.incrementAndGet();
        return count <= limit;

    }

    private String currentWindowKey(String key){
        long now = System.currentTimeMillis();
        long windowIndex =  now /windowSizeMs;
        return key + ":" + windowIndex ;
    }

}
