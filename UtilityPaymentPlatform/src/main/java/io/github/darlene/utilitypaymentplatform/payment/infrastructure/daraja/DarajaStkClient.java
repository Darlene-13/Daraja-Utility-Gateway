package io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja;


import io.github.darlene.utilitypaymentplatform.payment.domain.StkResult;
import io.github.darlene.utilitypaymentplatform.payment.domain.Transaction;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class DarajaStkClient {

    public final String shortCode;  // Business paybill number side.
    public final String passKey;
    public final String stkPushUrl;
    public final String callBackUrl;
    public final DarajaAuthClient darajaAuthClient;
    public final int MAX_RETRIES = 3; //For DNS connections
    public final int RETRY_DELAY_MS = 500;


    //Method to initiate stk push, it takes the transaction in the db with the status INITIATED
    public StkResult initiateStkPush(Transaction transaction){
        String token = darajaAuthClient.getAccessToken();

    }

    private String buildPassword




}
