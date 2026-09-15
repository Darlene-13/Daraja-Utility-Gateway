package io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja;


import io.github.darlene.utilitypaymentplatform.payment.domain.StkResult;
import io.github.darlene.utilitypaymentplatform.payment.domain.Transaction;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

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
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmSS"));
        String password = generatePassword(timestamp);
        //Build the initiate stk body
        //Use linkedhashmap because we maintain the insertion order
        Map<String, Object> stkRequest = new LinkedHashMap<>();
        stkRequest.put("BusinessShortCode", shortCode);
        stkRequest.put("Password", password);
        stkRequest.put("Timestamp", timestamp);
        stkRequest.put("TransactionType", "CustomerPayBillOnline");
        stkRequest.put("Amount", transaction.getAmount());
        stkRequest.put("partyA", transaction.getPhoneNumber());
        stkRequest.put("PartyB", shortCode);
        stkRequest.put("PhoneNumber", transaction.getPhoneNumber());
        stkRequest.put("CallBackURL", callBackUrl);
        stkRequest.put("AmountReference", transaction.getMeter()); // Confirm
        stkRequest.put("TransactionDesc", "Utility payment");

        return callWithRetry();

    }

    private String generatePassword(String timestamp){
        String raw = shortCode + passKey + timestamp;
        byte[] getBytes = raw.getBytes(StandardCharsets.UTF_8);
        String encodedPassword = Base64.getEncoder().encodeToString(getBytes);
        return encodedPassword;
    }

    private StkResult callWithRetry(){

    }




}
