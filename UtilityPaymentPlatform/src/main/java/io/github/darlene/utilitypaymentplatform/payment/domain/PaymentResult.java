package io.github.darlene.utilitypaymentplatform.payment.domain;

import java.util.UUID;

public record PaymentResult(
        UUID transactionId,
        Status status,
        String checkoutRequestId,
        String errorMessage
) {
    public static PaymentResult from(Transaction transaction) {
        return new PaymentResult(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getCheckoutRequestId(),
                transaction.getFailureReason()
        );
    }
}