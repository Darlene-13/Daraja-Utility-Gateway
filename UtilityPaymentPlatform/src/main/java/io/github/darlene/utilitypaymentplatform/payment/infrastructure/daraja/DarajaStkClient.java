package io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja;


import io.github.darlene.utilitypaymentplatform.payment.domain.DarajaStkResponse;
import io.github.darlene.utilitypaymentplatform.payment.domain.StkResult;
import io.github.darlene.utilitypaymentplatform.payment.domain.Transaction;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

@AllArgsConstructor
@Component
public class DarajaStkClient {

    public final String shortCode;  // Business playbill number side.
    public final String passKey;
    public final String stkPushUrl;
    public final String callBackUrl;
    public final DarajaAuthClient darajaAuthClient;
    public final int MAX_ATTEMPTS = 3; //For DNS connections
    public final int RETRY_DELAY_MS = 500;
    public HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;


    //Method to initiate stk push, it takes the transaction in the db with the status INITIATED
    public StkResult initiateStkPush(Transaction transaction) throws InterruptedException {
        String token = darajaAuthClient.getAccessToken();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = generatePassword(timestamp);


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

        return callWithRetry(stkRequest, token);

    }

    private String generatePassword(String timestamp){
        String raw = shortCode + passKey + timestamp;
        byte[] getBytes = raw.getBytes(StandardCharsets.UTF_8);
        String encodedPassword = Base64.getEncoder().encodeToString(getBytes);
        return encodedPassword;
    }

    private StkResult callWithRetry(Map<String, Object> stkRequest, String token) throws InterruptedException {
        String json = objectMapper.writeValueAsString(stkRequest);
        // Build the request for us to get a response we need to actually send a request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stkPushUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        int attempts = 0;
        while (attempts < MAX_ATTEMPTS){
            attempts +=1;
            try{
                // HttpResponse<String> is the body build in request
                HttpResponse<String> response =
                        client.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                return parseResponse(response);


            } catch (IOException e){
                if (attempts < MAX_ATTEMPTS){
                    sleep(RETRY_DELAY_MS);
                    continue;
                }

                return StkResult.failure("Network failure after " + attempts + " attempts: " + e.getMessage());

            }
        }
        return StkResult.failure("Exhausted retry attempts");
    }

    private StkResult parseResponse(HttpResponse<String> response) {
        //HTTP LEVEL
        // Did daraja actually get the result
        // 200 == OK
        if(response.statusCode() != 200){
            return StkResult.failure(response.body());
        }

        String body = response.body();
        //Deserialize the body
        DarajaStkResponse darajaResponse = objectMapper.readValue(
                body,
                DarajaStkResponse.class
        );

        //Daraja level
        // Did daraja accept the request
        if (!darajaResponse.ResponseCode().equals("0")){
            return StkResult.failure(darajaResponse.ResponseDescription());
        }


        //Application level
        StkResult result = StkResult.success(
                darajaResponse.MerchantRequestId(),
                darajaResponse.CheckoutRequestId()
        );


        return result;
    }

}
