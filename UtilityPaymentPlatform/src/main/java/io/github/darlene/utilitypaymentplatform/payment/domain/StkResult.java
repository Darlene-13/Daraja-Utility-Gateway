package io.github.darlene.utilitypaymentplatform.payment.domain;

public record StkResult(
        boolean success,
        String merchantRequestId,
        String checkoutRequestId,
        String errorMessage
) {
    public static StkResult success(String merchantRequestId, String checkoutRequestId) {
        return new StkResult(true, merchantRequestId, checkoutRequestId, null);
    }

    public static StkResult failure(String errorMessage) {
        return new StkResult(false, null, null, errorMessage);
    }
}