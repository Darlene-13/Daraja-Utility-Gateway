package io.github.darlene.utilitypaymentplatform.payment;


import io.github.darlene.utilitypaymentplatform.meter.Meter;
import io.github.darlene.utilitypaymentplatform.meter.MeterValidationService;
import io.github.darlene.utilitypaymentplatform.payment.domain.*;
import io.github.darlene.utilitypaymentplatform.payment.exception.TooManyRequestsException;
import io.github.darlene.utilitypaymentplatform.payment.infrastructure.SlidingWindowRateLimiter;
import io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja.DarajaStkClient;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@AllArgsConstructor
public class InitiatePaymentUseCase {

    public final SlidingWindowRateLimiter slidingWindowRateLimiter;
    public final DarajaStkClient stkClient;
    public final TransactionRepository transactionRepository;  // Check whether we have initiated a transaction
    public final MeterValidationService meterValidationService;

    public PaymentResult execute(InitiatePaymentCommand paymentCommand) throws InterruptedException {
        // Check is allowed method in rate limiter
        if (!slidingWindowRateLimiter.isAllowed(paymentCommand.phoneNumber())) {
            throw new TooManyRequestsException("Too many payment attempts, please wait before trying again");
        }

        if (transactionRepository.existsByPhoneNumberAndStatus(paymentCommand.phoneNumber(), Status.PENDING_CONFIRMATION)) {
            throw new TooManyRequestsException.PaymentAlreadyInFlightException("A payment for this phone number is already pending");
        }

        Meter meter = meterValidationService.validate(paymentCommand.meterNumber());
        Transaction transaction = buildAndSaveTransaction(paymentCommand, meter);

        StkResult result = stkClient.initiateStkPush(transaction);

        // Update the status
        updateAfterStkResponse(transaction, result);
        return PaymentResult.from(transaction);

    }

    private Transaction buildAndSaveTransaction(InitiatePaymentCommand paymentCommand, Meter meter) {
        Transaction transaction = new Transaction();

        transaction.setMeter(meter);
        transaction.setCustomer(meter.getCustomer());
        transaction.setAmount(paymentCommand.amount());
        transaction.setPhoneNumber(paymentCommand.phoneNumber());
        transaction.setStatus(Status.INITIATED);
        return transactionRepository.save(transaction);
    }


    private void updateAfterStkResponse(Transaction transaction, StkResult stkResult) {
        if (stkResult.success()) {
            transaction.setStatus(Status.PENDING_CONFIRMATION);
            transaction.setCheckoutRequestId(stkResult.checkoutRequestId());
        } else {
            transaction.setStatus(Status.FAILED);
            transaction.setFailureReason(stkResult.errorMessage());
        }
        transactionRepository.save(transaction);
    }

}
