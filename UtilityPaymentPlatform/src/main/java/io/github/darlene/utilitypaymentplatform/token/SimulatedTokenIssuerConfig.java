package io.github.darlene.utilitypaymentplatform.token;

import io.github.darlene.utilitypaymentplatform.meter.MeterType;
import io.github.darlene.utilitypaymentplatform.token.tokenissuer.TokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// We have no real KPLC or water provider yet. For each meter type whose base-url is blank,
// register a SimulatedTokenIssuer instead; setting the URL switches to the real API client.
// The conditions are the exact opposite of the ones on ElectricityApiClient and WaterApiClient,
// so there is always exactly one issuer per meter type.
@Configuration
public class SimulatedTokenIssuerConfig {

    @Bean
    @ConditionalOnExpression("'${electricity.api.base-url:}'.isBlank()")
    public TokenIssuer simulatedElectricityIssuer(@Value("${token.simulated.failure-rate:0.0}") double failureRate) {
        return new SimulatedTokenIssuer(MeterType.ELECTRICITY, failureRate);
    }

    @Bean
    @ConditionalOnExpression("'${water.api.base-url:}'.isBlank()")
    public TokenIssuer simulatedWaterIssuer(@Value("${token.simulated.failure-rate:0.0}") double failureRate) {
        return new SimulatedTokenIssuer(MeterType.WATER, failureRate);
    }
}
