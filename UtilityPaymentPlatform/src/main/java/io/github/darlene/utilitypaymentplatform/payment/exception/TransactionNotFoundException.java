package io.github.darlene.utilitypaymentplatform.payment.exception;

import org.hibernate.validator.constraints.UUID;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID message) {
        super((Throwable) message);
    }

    public TransactionNotFoundException(java.util.UUID id) {
    }
}
