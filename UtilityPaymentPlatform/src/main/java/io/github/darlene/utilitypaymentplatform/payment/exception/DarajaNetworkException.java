package io.github.darlene.utilitypaymentplatform.payment.exception;

public class DarajaNetworkException extends RuntimeException {

    public DarajaNetworkException(String message) {
        super(message);
    }

    public DarajaNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}