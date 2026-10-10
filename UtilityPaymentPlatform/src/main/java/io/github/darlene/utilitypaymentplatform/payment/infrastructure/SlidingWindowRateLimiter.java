package io.github.darlene.utilitypaymentplatform.payment.infrastructure;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;


public class SlidingWindowRateLimiter implements RateLimiter{

    private final Cache<String, Deque<Long>> cache;
    private final long limit;
    private final long windowSizeMs;

    public SlidingWindowRateLimiter(long limit, long windowSizeMs) {
        if (limit <= 0 || windowSizeMs <= 0) {
            throw new IllegalArgumentException("limit and windowSizeMs must be positive");
        }
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        //Use the expireAfterAccess to delete expired phone numbers instead of keeping them in the memory for so long.
        //Expiry follows the window, a key idle for a full window has nothing left to count anyway.
        this.cache = Caffeine.newBuilder().expireAfterAccess(Duration.ofMillis(windowSizeMs)).build();
    }


    @Override
    public boolean isAllowed(String key){
        //Get the deque if it does not exist create an empty array deque
        Deque<Long> deque = cache.get(
                key, k -> new ArrayDeque<>()
        );

        //Lock the deque to prevent race conditions
        synchronized (deque){
            long now = System.currentTimeMillis();
            long windowStart = now - windowSizeMs;
            trimExpired(deque, windowStart);

            if(countWithinWindow(deque) < limit){
                recordAttempt(deque, now);
                return true;
            }
        }
        return false;
    }

    private void trimExpired(Deque<Long> deque, long windowStart){
        while(!deque.isEmpty() && deque.peekFirst() < windowStart){
            deque.pollFirst();
        }
    }

    private int countWithinWindow(Deque<Long> deque){
       return deque.size();
    }

    private void recordAttempt(Deque<Long> deque, long now){
        deque.addLast(now);
    }

}