package io.github.darlene.utilitypaymentplatform.ussd;

import io.github.darlene.utilitypaymentplatform.payment.InitiatePaymentUseCase;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

// Decides which USSD screen comes next. Nothing is stored between key presses:
// AT sends everything the customer has typed so far in "text", joined by '*',
// so where they are in the menu is worked out from that string alone.
//
//   ""               -> CON Pay for:\n1. Electricity\n2. Water
//   "1"              -> CON Enter meter number
//   "1*MTR-0002"     -> CON Enter amount (KES)
//   "1*MTR-0002*50"  -> END ... and start the payment
@Service
@AllArgsConstructor
public class UssdMenuService {

    // Same core the REST PaymentController uses: USSD is just another way in
    private final InitiatePaymentUseCase initiatePaymentUseCase;

    public String handle(String phoneNumber, String text) {
        // TODO step 1: return the main menu when text is empty
        // TODO step 2: split text on "*" and switch on how many parts there are
        // TODO step 3: on the last step, build an InitiatePaymentCommand and call the use case.
        //   - phone: AT sends +2547..., the app expects 2547... (see normalisePhone)
        //   - amount: check it is a whole number >= 1, @Valid does not run on this path
        //   - catch the use case's exceptions (meter not found, too many requests) and
        //     turn them into END messages, AT cannot show a JSON error to the customer
        throw new UnsupportedOperationException("not implemented yet");
    }

    // TODO step 3: "+254790757706" -> "254790757706"
    private String normalisePhone(String atPhoneNumber) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    private String con(String message) {
        return "CON " + message;
    }

    private String end(String message) {
        return "END " + message;
    }
}
