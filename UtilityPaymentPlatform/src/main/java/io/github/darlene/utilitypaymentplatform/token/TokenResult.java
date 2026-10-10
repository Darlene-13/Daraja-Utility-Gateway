package io.github.darlene.utilitypaymentplatform.token;

import java.math.BigDecimal;

public record TokenResult(
        String token,
        BigDecimal units,
        String providerReference
) {
}
