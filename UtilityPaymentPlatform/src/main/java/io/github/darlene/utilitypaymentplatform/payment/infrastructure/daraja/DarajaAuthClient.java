package io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja;

import io.github.darlene.utilitypaymentplatform.payment.domain.TokenResponse;
import io.github.darlene.utilitypaymentplatform.payment.exception.DarajaAuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

// One bean shared by the STK push and STK query clients, so they share the cached token
@Component
public class DarajaAuthClient {

    private static final long SAFETY_MARGIN_SECONDS = 60;
    private final String consumerKey;
    private final String consumerSecret;
    private final String tokenUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    public DarajaAuthClient(@Value("${daraja.consumer-key}") String consumerKey,
                            @Value("${daraja.consumer-secret}") String consumerSecret,
                            @Value("${daraja.token-url}") String tokenUrl,
                            ObjectMapper objectMapper) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.tokenUrl = tokenUrl;
        this.objectMapper = objectMapper;
    }

    public synchronized String getAccessToken() {
        Instant now = Instant.now();
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
                .header("Authorization", "Basic " + encodedCredentials)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();


        HttpResponse<String> response;

        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DarajaAuthException("Interrupted while fetching Daraja token", e);
        } catch (IOException e){
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

    // Daraja sends {"access_token": "...", "expires_in": "3599"}, expires_in as a string
    private TokenResponse parseTokenResponse(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            String accessToken = json.path("access_token").asText();
            if (accessToken.isBlank()) {
                throw new DarajaAuthException("Token response has no access_token");
            }
            return new TokenResponse(accessToken, Long.parseLong(json.path("expires_in").asText("3599")));
        } catch (DarajaAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new DarajaAuthException("Unreadable token response", e);
        }
    }
}
