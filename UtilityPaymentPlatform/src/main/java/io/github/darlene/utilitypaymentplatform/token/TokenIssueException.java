package io.github.darlene.utilitypaymentplatform.token;

public class TokenIssueException extends RuntimeException {
    public TokenIssueException(String message) {
        super(message);
    }

    public TokenIssueException(String message, Throwable cause){
      super(message, cause);
    }
}
