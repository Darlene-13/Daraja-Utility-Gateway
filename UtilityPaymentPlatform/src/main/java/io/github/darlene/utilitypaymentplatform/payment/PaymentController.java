package io.github.darlene.utilitypaymentplatform.payment;


import io.github.darlene.utilitypaymentplatform.payment.domain.*;
import io.github.darlene.utilitypaymentplatform.payment.exception.TransactionNotFoundException;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("api/v1/payment")
@AllArgsConstructor
public class PaymentController {

    private final InitiatePaymentUseCase initiatePaymentUseCase;
    private final TransactionRepository transactionRepository;

    @PostMapping("/")
    public ResponseEntity<PaymentResponse> initiatePayment(@Valid @RequestBody PaymentRequest request) throws InterruptedException {

        var command = new InitiatePaymentCommand(request.phoneNumber(), request.meterNumber(), request.amount());
        PaymentResult result = initiatePaymentUseCase.execute(command);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PaymentResponse.from(result));

    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        return ResponseEntity.ok(PaymentResponse.from(PaymentResult.from(transaction)));
    }
}
