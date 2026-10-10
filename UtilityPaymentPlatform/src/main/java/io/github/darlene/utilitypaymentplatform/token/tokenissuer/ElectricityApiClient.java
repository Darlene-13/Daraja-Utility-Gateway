package io.github.darlene.utilitypaymentplatform.token.tokenissuer;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.darlene.utilitypaymentplatform.meter.MeterType;
import io.github.darlene.utilitypaymentplatform.token.TokenIssueException;
import io.github.darlene.utilitypaymentplatform.token.TokenResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;

// Only when a provider URL is configured, otherwise SimulatedTokenIssuerConfig stands in
@Component
@ConditionalOnExpression("!'${electricity.api.base-url:}'.isBlank()")
public class ElectricityApiClient implements TokenIssuer {

    private final RestClient restClient;

    public ElectricityApiClient(@Value("${electricity.api.base-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public MeterType type() {
        return MeterType.ELECTRICITY;
    }

    @Override
    public TokenResult issueToken(String meterNumber, BigDecimal amount, String reference) {
        try {
            ElectricityResponse resp = restClient.post()
                    .uri("/vend")
                    .header("Idempotency-Key", reference)
                    .body(new ElectricityRequest(meterNumber, amount))
                    .retrieve()
                    .body(ElectricityResponse.class);

            if (resp == null || resp.token() == null) {
                throw new TokenIssueException("Electricity API returned no token for " + reference);
            }
            return new TokenResult(resp.token(), resp.kwh(), resp.receiptNo());
        } catch (RestClientException e) {
            throw new TokenIssueException("Electricity API call failed for " + reference, e);
        }
    }

    // different field names from water on purpose: the interface hides this from the worker
    private record ElectricityRequest(@JsonProperty("meter_no") String meterNo, BigDecimal amount) {}
    private record ElectricityResponse(String token, BigDecimal kwh, @JsonProperty("receipt_no") String receiptNo) {}
}