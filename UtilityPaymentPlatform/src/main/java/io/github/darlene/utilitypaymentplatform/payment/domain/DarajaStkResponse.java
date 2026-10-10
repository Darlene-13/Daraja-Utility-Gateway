package io.github.darlene.utilitypaymentplatform.payment.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
// Daraja back to your Spring Boot application.
// Daraja spells the ids with a capital "ID", which does not match the record
// component names, so they are mapped explicitly or they would come back null
public record DarajaStkResponse(
        @JsonProperty("MerchantRequestID") String MerchantRequestId,
        @JsonProperty("CheckoutRequestID") String CheckoutRequestId,
        String ResponseCode,
        String ResponseDescription,
        String CustomerMessage
) {
}
