package io.github.darlene.utilitypaymentplatform.token;

public record TokenResult(
        boolean success,
        String token,
        String errorMessage
) {

    public static TokenResult success(String token){
        return new TokenResult(true,token, null);
    }


    public static TokenResult failure(String errorMessage){
        return new TokenResult(false, null, errorMessage);
    }
}
