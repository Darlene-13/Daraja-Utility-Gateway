package io.github.darlene.utilitypaymentplatform.payment.exception;

public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }

    public static class PaymentAlreadyInFlightException extends RuntimeException {
        public PaymentAlreadyInFlightException(String message) { super(message); }
    }
}
