package io.github.darlene.utilitypaymentplatform.payment.infrastructure;

public class TokenBucketRateLimiter implements RateLimiter {


    @Override
    public boolean isAllowed(String key) {
        return false;
    }

    private void refill(String key){

    }

    private void tryConsume(String key){

    }

}
