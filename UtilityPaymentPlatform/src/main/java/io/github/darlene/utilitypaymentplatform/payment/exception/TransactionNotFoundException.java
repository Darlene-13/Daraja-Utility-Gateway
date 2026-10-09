package io.github.darlene.utilitypaymentplatform.payment.exception;
import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(UUID message) {
        super((Throwable) message);
    }
}
