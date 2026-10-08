package io.github.darlene.utilitypaymentplatform.token;


import io.github.darlene.utilitypaymentplatform.token.tokenissuer.TokenIssuer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SimulatedTokenIssuer implements TokenIssuer {

    private final MeterType type;
    private final double failureRate;   // 0.0 = never fails, 0.3 = fails 30% of calls

    public SimulatedTokenIssuer(MeterType type, double failureRate) {
        this.type = type;
        this.failureRate = failureRate;
    }

    @Override
    public MeterType supportedMeterType() {
        return type;
    }

    @Override
    public TokenResult issueToken(String meterNumber, BigDecimal amount) {
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new TokenIssueException("Simulated " + type + " provider failure for " + reference);
        }

        // same reference -> same token, like a real idempotency key
        String token = tokenFrom(type + ":" + reference);

        // fake tariff: 1 unit costs 25 (kWh for electricity, m3 for water)
        BigDecimal units = amount.divide(BigDecimal.valueOf(25), 2, RoundingMode.HALF_UP);

        return new TokenResult(token, units, "SIM-" + type + "-" + reference);
    }


    private String tokenFrom(String seed) {
        UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        long hi = Math.abs(uuid.getMostSignificantBits() % 10_000_000_000L);
        long lo = Math.abs(uuid.getLeastSignificantBits() % 10_000_000_000L);
        return String.format("%010d%010d", hi, lo);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(200, 800));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}