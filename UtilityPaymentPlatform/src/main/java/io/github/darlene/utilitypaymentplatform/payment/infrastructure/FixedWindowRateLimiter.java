package io.github.darlene.utilitypaymentplatform.payment.infrastructure;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class FixedWindowRateLimiter implements RateLimiter {

    private final long windowSizeMs;
    private final Cache<String, AtomicInteger> cache;
    private final int limit;

    public FixedWindowRateLimiter(long windowSizeMs, int limit) {
        if (windowSizeMs <= 0 || limit <= 0) {
            throw new IllegalArgumentException("windowSizeMs and limit must be positive");
        }
        this.windowSizeMs = windowSizeMs;
        this.limit = limit;
        //Built here, not at the field, so windowSizeMs is already set when the expiry is computed
        this.cache = Caffeine.newBuilder().expireAfterAccess(Duration.ofMillis(windowSizeMs * 2)).build();
    }

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
