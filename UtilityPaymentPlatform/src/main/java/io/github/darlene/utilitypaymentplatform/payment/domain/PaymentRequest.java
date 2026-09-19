package io.github.darlene.utilitypaymentplatform.payment.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^254[17]\\d{8}$", message = "Phone number must be a valid Kenyan number")
        String phoneNumber,

        @NotBlank(message = "Meter number is required")
        String meterNumber,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "1.0", message = "Amount must be at least 1")
        BigDecimal amount
) {}