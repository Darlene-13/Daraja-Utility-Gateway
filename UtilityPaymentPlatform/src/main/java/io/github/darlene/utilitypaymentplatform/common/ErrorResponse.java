package io.github.darlene.utilitypaymentplatform.common;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ErrorResponse(
        Integer status,
        String error,
        String message,
        LocalDateTime timestamp,
        String path
) {
}
