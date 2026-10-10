package io.github.darlene.utilitypaymentplatform.callback;

import io.github.darlene.utilitypaymentplatform.callback.infrastructure.CallBackIngestPublisher;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// daraja.callback-url must point here. The body is taken as a raw String and put on the
// stream untouched: no parsing or DB work here, so we answer Safaricom fast.
@Slf4j
@RestController
@RequestMapping("/api/v1/callback")
@AllArgsConstructor
public class CallbackReceiverController {

    private final CallBackIngestPublisher callBackIngestPublisher;

    @PostMapping("/stk")
    public ResponseEntity<Map<String, Object>> receiveStkCallback(@RequestBody String rawPayLoad){
        try{
            callBackIngestPublisher.publishPayment(rawPayLoad);
            return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
        } catch (Exception e){
            // Safaricom won't resend, ReconciliationJob picks this transaction up instead
            log.error("Could not queue STK callback, reconciliation will resolve it", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ResultCode", 1, "ResultDesc", "Rejected"));
        }
    }

}
