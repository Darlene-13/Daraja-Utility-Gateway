package io.github.darlene.utilitypaymentplatform.payment.infrastructure;

public class LeakyBucketRateLimiter implements RateLimiter {

    @Override
    public boolean isAllowed(String key){
        return true;
    }

    //Drains the bucket based on elapsed time
    private void leak(String key){

    }

    //Adds the request if there is room
    private void tryAdd(String key){

    }

}
