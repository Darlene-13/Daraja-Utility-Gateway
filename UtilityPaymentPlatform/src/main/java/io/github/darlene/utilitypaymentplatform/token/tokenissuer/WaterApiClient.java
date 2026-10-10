package io.github.darlene.utilitypaymentplatform.token.tokenissuer;

import io.github.darlene.utilitypaymentplatform.meter.MeterType;
import io.github.darlene.utilitypaymentplatform.token.TokenIssueException;
import io.github.darlene.utilitypaymentplatform.token.TokenResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class WaterApiClient implements TokenIssuer {

    private final RestClient restClient;

    public WaterApiClient(@Value("${water.api.base-url}") String baseUrl) {
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
        return MeterType.WATER;
    }

    @Override
    public TokenResult issueToken(String meterNumber, BigDecimal amount, String reference) {
        try {
            WaterResponse resp = restClient.post()
                    .uri("/tokens")
                    .header("Idempotency-Key", reference)
                    .body(new WaterRequest(meterNumber, amount))
                    .retrieve()
                    .body(WaterResponse.class);

            if (resp == null || resp.token() == null) {
                throw new TokenIssueException("Water API returned no token for " + reference);
            }
            return new TokenResult(resp.token(), resp.units(), resp.reference());
        } catch (RestClientException e) {
            throw new TokenIssueException("Water API call failed for " + reference, e);
        }
    }

    // provider-specific wire format, private to this class
    private record WaterRequest(String meterNumber, BigDecimal amount) {}
    private record WaterResponse(String token, BigDecimal units, String reference) {}
}