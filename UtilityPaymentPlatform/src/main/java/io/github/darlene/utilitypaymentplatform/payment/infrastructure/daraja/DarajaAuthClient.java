package io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja;

import io.github.darlene.utilitypaymentplatform.payment.domain.TokenResponse;
import io.github.darlene.utilitypaymentplatform.payment.exception.DarajaAuthException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
public class DarajaAuthClient {

    private static final long SAFETY_MARGIN_SECONDS = 60;
    private final String consumerKey;
    private final String consumerSecret;
    private final String tokenUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;


    // Every part calls when it needs a token
    public synchronized String getAccessToken() {
        Instant now = Instant.now();

        // Do we have something we can use ..
        if (cachedToken != null && now.isBefore(expiresAt.minusSeconds(SAFETY_MARGIN_SECONDS))) {
            return cachedToken;
        }

        return refreshToken();
    }

    private String refreshToken() {
        String credentials = consumerKey + ":" + consumerSecret;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Authorization", "Basic" + encodedCredentials)
                .GET()
                .build();


        HttpResponse<String> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e){
            throw new DarajaAuthException("Could not reach Daraja for Token", e);
        }

        if (response.statusCode() != 200) {
            throw new DarajaAuthException("Token request rejected: " + response.body());
        }

        TokenResponse tokenResponse = parseTokenResponse(response.body());
        cachedToken = tokenResponse.accessToken();
        expiresAt = Instant.now().plusSeconds(tokenResponse.expiresIn());
        return cachedToken;
    }

    private TokenResponse parseTokenResponse(String body) {
        throw new UnsupportedOperationException("parseTokenResponse not implemented yet");
    }
}