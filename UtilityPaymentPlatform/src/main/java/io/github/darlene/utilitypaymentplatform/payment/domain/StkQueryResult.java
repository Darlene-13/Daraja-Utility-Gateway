package io.github.darlene.utilitypaymentplatform.payment.domain;

// What Daraja's STK Push Query says about a payment
public record StkQueryResult(State state, String description) {

    public enum State {
        PAID,               // ResultCode 0
        FAILED,             // any other ResultCode: cancelled, timed out, wrong PIN, no funds...
        STILL_PROCESSING,   // customer hasn't answered the prompt yet, ask again later
        UNKNOWN             // we could not get an answer (network, auth, unexpected response)
    }

    public static StkQueryResult of(State state, String description) {
        return new StkQueryResult(state, description);
    }
}
