package io.github.darlene.utilitypaymentplatform.payment.domain;

public record DarajaStkResponse(
        String MerchantRequestId,
        String CheckoutRequestId,
        String ResponseCode,
        String ResponseDescription,
        String CustomerMessage
) {
}
