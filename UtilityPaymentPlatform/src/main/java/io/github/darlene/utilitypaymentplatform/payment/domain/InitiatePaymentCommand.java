package io.github.darlene.utilitypaymentplatform.payment.domain;

import java.math.BigDecimal;

public record InitiatePaymentCommand(
        String phoneNumber, String meterNumber, BigDecimal amount
) {
}
