package io.github.darlene.utilitypaymentplatform.token.model;

import io.github.darlene.utilitypaymentplatform.meter.MeterType;
import java.math.BigDecimal;
import java.util.UUID;


public record PaymentEventPayload (
        UUID transactionId,
        String checkoutRequestId,
        String meterNumber,
        MeterType meterType,
        BigDecimal amount

){

}
