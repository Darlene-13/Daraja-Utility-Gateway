package io.github.darlene.utilitypaymentplatform.token.tokenissuer;


import io.github.darlene.utilitypaymentplatform.token.MeterType;
import io.github.darlene.utilitypaymentplatform.token.TokenResult;

import java.math.BigDecimal;

public class ElectricityApiClient implements TokenIssuer{

    @Override
    public MeterType supporttedMeterType() {
        return null;
    }

    @Override
    public TokenResult issueToken(String meterNumber, BigDecimal amount) {
        return null;
    }
}