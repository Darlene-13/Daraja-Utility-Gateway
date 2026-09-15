package io.github.darlene.utilitypaymentplatform.payment.exception;

public class DarajaAuthException extends RuntimeException {
    public DarajaAuthException(String message) {
        super(message);
    }

    public DarajaAuthException(String message, Throwable cause){
        super(message, cause);
    }
}
