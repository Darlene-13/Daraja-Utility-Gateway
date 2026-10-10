package io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja;

import io.github.darlene.utilitypaymentplatform.payment.domain.StkQueryResult;
import io.github.darlene.utilitypaymentplatform.payment.domain.StkQueryResult.State;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

// Asks Daraja for the result of an STK push, for when the callback never reached us.
// Note the query does not return the MpesaReceiptNumber, only the result code.
@Slf4j
@Component
public class DarajaStkQueryClient {

    // Daraja's answer while the customer still has the prompt open
    private static final String STILL_PROCESSING_ERROR = "500.001.1001";
    private static final String STILL_PROCESSING_RESULT = "4999";

    private final String shortCode;
    private final String passKey;
    private final String queryUrl;
    private final DarajaAuthClient darajaAuthClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DarajaStkQueryClient(@Value("${daraja.shortcode}") String shortCode,
                                @Value("${daraja.passkey}") String passKey,
                                @Value("${daraja.stk-query-url}") String queryUrl,
                                DarajaAuthClient darajaAuthClient,
                                ObjectMapper objectMapper) {
        this.shortCode = shortCode;
        this.passKey = passKey;
        this.queryUrl = queryUrl;
        this.darajaAuthClient = darajaAuthClient;
        this.objectMapper = objectMapper;
    }

    public StkQueryResult query(String checkoutRequestId) {
        // Daraja validates the timestamp in Kenyan time, whatever zone the server runs in
        String timestamp = LocalDateTime.now(ZoneId.of("Africa/Nairobi"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", shortCode);
        body.put("Password", generatePassword(timestamp));
        body.put("Timestamp", timestamp);
        body.put("CheckoutRequestID", checkoutRequestId);

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(queryUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + darajaAuthClient.getAccessToken())
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StkQueryResult.of(State.UNKNOWN, "Interrupted");
        } catch (IOException | RuntimeException e) {
            // network down or auth failed: the next reconciliation round asks again
            log.warn("STK query for {} failed", checkoutRequestId, e);
            return StkQueryResult.of(State.UNKNOWN, e.getMessage());
        }

        return parse(checkoutRequestId, response);
    }

    private StkQueryResult parse(String checkoutRequestId, HttpResponse<String> response) {
        JsonNode json;
        try {
            json = objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.warn("Unreadable STK query response for {}: {}", checkoutRequestId, response.body());
            return StkQueryResult.of(State.UNKNOWN, "Unreadable response");
        }

        // Daraja reports "still processing" as an HTTP error, not as a result
        if (response.statusCode() != 200) {
            String errorCode = json.path("errorCode").asText();
            String errorMessage = json.path("errorMessage").asText();
            if (STILL_PROCESSING_ERROR.equals(errorCode)) {
                return StkQueryResult.of(State.STILL_PROCESSING, errorMessage);
            }
            log.warn("STK query for {} rejected: {} {}", checkoutRequestId, errorCode, errorMessage);
            return StkQueryResult.of(State.UNKNOWN, errorMessage);
        }

        if (!"0".equals(json.path("ResponseCode").asText())) {
            return StkQueryResult.of(State.UNKNOWN, json.path("ResponseDescription").asText());
        }

        String resultCode = json.path("ResultCode").asText();
        String resultDesc = json.path("ResultDesc").asText();
        if ("0".equals(resultCode)) {
            return StkQueryResult.of(State.PAID, resultDesc);
        }
        if (STILL_PROCESSING_RESULT.equals(resultCode)) {
            return StkQueryResult.of(State.STILL_PROCESSING, resultDesc);
        }
        // same rule as CallBackWorker: any non-zero result code is a failed payment
        return StkQueryResult.of(State.FAILED, resultDesc);
    }

    private String generatePassword(String timestamp) {
        String raw = shortCode + passKey + timestamp;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
