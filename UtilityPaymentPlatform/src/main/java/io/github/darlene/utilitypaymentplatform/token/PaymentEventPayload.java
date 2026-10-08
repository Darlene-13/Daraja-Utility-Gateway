package io.github.darlene.utilitypaymentplatform.token;

import java.math.BigDecimal;

public record PaymentEventPayload (
        String checkoutRequestId,
        String meterNumber,
        MeterType meterType,
        BigDecimal amount

){

}
