package io.github.darlene.utilitypaymentplatform.payment.infrastructure;


import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class CompositeRateLimiter implements RateLimiter {

    private final List<RateLimiter> limiters;

    @Override
    public boolean isAllowed(String key) {
        for(RateLimiter limiter: limiters){
            if (!limiter.isAllowed(key)){
                return false;
            }
        }
        return true;
    }
}
