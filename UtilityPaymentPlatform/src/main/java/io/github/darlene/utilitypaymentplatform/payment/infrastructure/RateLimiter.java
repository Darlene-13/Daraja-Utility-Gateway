package io.github.darlene.utilitypaymentplatform.payment.infrastructure;


// This Interface will be extended by the different rate limiting algorithms: Token Bucket, Sliding Window, Fixed Window algorithms, Leaky Bucket algorithm
public interface RateLimiter {

    public boolean isAllowed(String key);

}
