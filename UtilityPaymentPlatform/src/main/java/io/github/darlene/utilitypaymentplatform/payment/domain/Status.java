package io.github.darlene.utilitypaymentplatform.payment.domain;

public enum Status {

    INITIATED("Initiated"),
    PENDING_CONFIRMATION("Pending"),
    PAID("Paid"),
    FAILED("Failed"),
    TOKEN_ISSUED("Token issued"),
    FAILED_TOKEN_ISSUANCE("Failed token issuance");

    private String description;

    Status(String description){
        this.description = description;
    }
}
