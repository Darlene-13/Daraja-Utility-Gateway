package io.github.darlene.utilitypaymentplatform.token.tokenissuer;


import io.github.darlene.utilitypaymentplatform.token.MeterType;
import io.github.darlene.utilitypaymentplatform.token.TokenResult;

import java.math.BigDecimal;

public interface TokenIssuer {

    public MeterType supporttedMeterType();

    public TokenResult issueToken(String meterNumber, BigDecimal amount);

}
