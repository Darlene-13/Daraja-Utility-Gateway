package io.github.darlene.utilitypaymentplatform.token.tokenissuer;


import io.github.darlene.utilitypaymentplatform.meter.MeterType;
import io.github.darlene.utilitypaymentplatform.token.TokenResult;

import java.math.BigDecimal;

public interface TokenIssuer {

    /** Which meter type this implementation handles. TokenProcessor uses it to route. */
    MeterType type();

    /**
     * @param reference stable id for this payment (checkoutRequestId). Sent to the provider
     *                  as an idempotency key so a retry returns the same token, not a new one.
     */
    TokenResult issueToken(String meterNumber, BigDecimal amount, String reference);
}