package io.github.darlene.utilitypaymentplatform.callback;

public record CallbackPayload(
        String checkoutRequestId, String resultCode) {
}
