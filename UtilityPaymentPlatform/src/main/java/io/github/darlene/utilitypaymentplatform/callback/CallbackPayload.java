package io.github.darlene.utilitypaymentplatform.callback;

public record CallbackPayload(
        String checkoutRequestId, boolean success, String errorMessage, String mpesaReceiptNumber) {
}
