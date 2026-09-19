package io.github.darlene.utilitypaymentplatform.payment.domain;

import org.hibernate.validator.constraints.UUID;

public record PaymentResponse(
        UUID transactionId,
        String status,
        String checkoutRequestId,
        String message
) {
    public static PaymentResponse from(PaymentResult result) {
        return new PaymentResponse(
                result.transactionId(),
                result.status().name(),
                result.checkoutRequestId(),
                result.errorMessage()
        );
    }
}