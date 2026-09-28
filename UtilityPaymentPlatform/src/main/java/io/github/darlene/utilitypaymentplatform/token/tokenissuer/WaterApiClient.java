package io.github.darlene.utilitypaymentplatform.token.tokenissuer;

import io.github.darlene.utilitypaymentplatform.token.MeterType;
import io.github.darlene.utilitypaymentplatform.token.TokenResult;

import java.math.BigDecimal;

public class WaterApiClient implements TokenIssuer{
    @Override
    public MeterType supportedMeterType() {
        return MeterType.Water;
    }

    @Override
    public TokenResult issueToken(String meterNumber, BigDecimal amount) {
        return TokenResult.success("WATER-" + meterNumber + "-" + amount);
    }
}
