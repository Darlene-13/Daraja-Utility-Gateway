package io.github.darlene.utilitypaymentplatform.common;

import io.github.darlene.utilitypaymentplatform.meter.MeterNumberNotFoundException;
import io.github.darlene.utilitypaymentplatform.payment.exception.DarajaAuthException;
import io.github.darlene.utilitypaymentplatform.payment.exception.TooManyRequestsException;
import io.github.darlene.utilitypaymentplatform.payment.exception.TransactionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler{

    @ExceptionHandler(DarajaAuthException.class)
    public ResponseEntity<ErrorResponse> handleDarajaAuthException(
          DarajaAuthException ex,
          HttpServletRequest request
    ){
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(MeterNumberNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMeterNumberNotFoundException(
            MeterNumberNotFoundException ex,
            HttpServletRequest request
    ){
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequestsException(
            TooManyRequestsException ex,
            HttpServletRequest request
    ){
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException ex,
            HttpServletRequest request
    ){
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(error);
    }
}