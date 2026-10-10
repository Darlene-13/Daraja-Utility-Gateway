package io.github.darlene.utilitypaymentplatform.ussd;

import io.github.darlene.utilitypaymentplatform.meter.Meter;
import io.github.darlene.utilitypaymentplatform.meter.MeterNumberNotFoundException;
import io.github.darlene.utilitypaymentplatform.meter.MeterType;
import io.github.darlene.utilitypaymentplatform.meter.MeterValidationService;
import io.github.darlene.utilitypaymentplatform.payment.InitiatePaymentUseCase;
import io.github.darlene.utilitypaymentplatform.payment.domain.InitiatePaymentCommand;
import io.github.darlene.utilitypaymentplatform.payment.domain.PaymentResult;
import io.github.darlene.utilitypaymentplatform.payment.domain.Status;
import io.github.darlene.utilitypaymentplatform.payment.exception.TooManyRequestsException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

// Decides which USSD screen comes next. Nothing is stored between key presses:
// AT sends everything the customer has typed so far in "text", joined by '*',
// so where they are in the menu is worked out from that string alone.
//
//   ""               -> CON Pay for:\n1. Electricity\n2. Water
//   "1"              -> CON Enter meter number
//   "1*MTR-0002"     -> CON Enter amount (KES)
//   "1*MTR-0002*50"  -> END ... and start the payment
@Slf4j
@Service
@AllArgsConstructor
public class UssdMenuService {

    // Same core the REST PaymentController uses: USSD is just another way in
    private final InitiatePaymentUseCase initiatePaymentUseCase;
    private final MeterValidationService meterValidationService;

    public String handle(String phoneNumber, String text) {

        if(text.isBlank()){
            return con("Pay for:\n1. Electricity\n2. Water");
        }

        String [] parts = text.split("\\*");

        MeterType chosenType = meterTypeFor(parts[0]);
        if (chosenType == null) {
            return end("Invalid choice. Please dial again and choose 1 or 2.");
        }

        return switch(parts.length){
            case 1 -> con("Enter meter number");
            case 2 -> checkMeter(parts[1].trim(), chosenType);
            case 3 -> pay(phoneNumber, parts[1].trim(), chosenType, parts[2].trim());
            default -> end("Invalid input. Please dial again.");
        };
    }

    private MeterType meterTypeFor(String choice) {
        return switch (choice) {
            case "1" -> MeterType.ELECTRICITY;
            case "2" -> MeterType.WATER;
            default -> null;
        };
    }

    //p; Checked here, before asking for the amount, so the customer finds out about a wrong
    // meter early. The use case looks it up again, but that is a Redis cache hit.
    private String checkMeter(String meterNumber, MeterType chosenType) {
        try {
            Meter meter = meterValidationService.validate(meterNumber);
            if (meter.getMeterType() != chosenType) {
                return end("Meter " + meterNumber + " is not a " + chosenType.name().toLowerCase() + " meter.");
            }
            return con("Enter amount (KES)");
        } catch (MeterNumberNotFoundException e) {
            return end("Meter " + meterNumber + " was not found.");
        }
    }

    private String pay(String phoneNumber, String meterNumber, MeterType chosenType, String rawAmount) {
        // @Valid on PaymentRequest only runs for the REST controller, so check the amount here
        if (!rawAmount.matches("\\d{1,6}") || Integer.parseInt(rawAmount) < 1) {
            return end("Invalid amount. Enter whole shillings, at least 1.");
        }

        // the meter could have been edited between key presses, check it again
        String meterCheck = checkMeter(meterNumber, chosenType);
        if (meterCheck.startsWith("END")) {
            return meterCheck;
        }

        var command = new InitiatePaymentCommand(normalisePhone(phoneNumber), meterNumber, new BigDecimal(rawAmount));
        try {
            PaymentResult result = initiatePaymentUseCase.execute(command);
            if (result.status() == Status.FAILED) {
                return end("We could not send the M-Pesa prompt. Please try again later.");
            }
            return end("Check your phone for the M-Pesa prompt to pay KES " + rawAmount
                    + ". Your token will be sent by SMS.");
        } catch (TooManyRequestsException e) {
            return end("Too many attempts. Please wait a minute and try again.");
        } catch (TooManyRequestsException.PaymentAlreadyInFlightException e) {
            return end("You have a payment in progress. Complete it or wait 2 minutes.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return end("Something went wrong. Please try again.");
        } catch (Exception e) {
            // AT can only show text, so anything unexpected still becomes an END message
            log.error("USSD payment failed for meter {}", meterNumber, e);
            return end("Something went wrong. Please try again.");
        }
    }

    // AT sends "+254790757706", the rest of the app uses "254790757706"
    private String normalisePhone(String atPhoneNumber) {
        return atPhoneNumber.startsWith("+") ? atPhoneNumber.substring(1) : atPhoneNumber;
    }

    private String con(String message) {
        return "CON " + message;
    }

    private String end(String message) {
        return "END " + message;
    }
}
