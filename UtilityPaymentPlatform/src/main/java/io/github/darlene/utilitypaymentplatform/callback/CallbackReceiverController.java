package io.github.darlene.utilitypaymentplatform.callback;

import io.github.darlene.utilitypaymentplatform.callback.infrastructure.CallBackIngestPublisher;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@AllArgsConstructor
public class CallbackReceiverController {

    private final CallBackIngestPublisher callBackIngestPublisher;

    public ResponseEntity<Void> receiveStkCallback(String rawPayLoad){
        try{
            callBackIngestPublisher.publish(rawPayLoad);
            return ResponseEntity.ok().build();
        } catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
