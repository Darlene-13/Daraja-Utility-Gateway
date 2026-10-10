package io.github.darlene.utilitypaymentplatform.payment.infrastructure.daraja;


import io.github.darlene.utilitypaymentplatform.payment.domain.DarajaStkResponse;
import io.github.darlene.utilitypaymentplatform.payment.domain.StkResult;
import io.github.darlene.utilitypaymentplatform.payment.domain.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DarajaStkClient {

    private static final int MAX_ATTEMPTS = 3; //For DNS connections
    private static final int RETRY_DELAY_MS = 500;

    private final String shortCode;  // Business playbill number side.
    private final String passKey;
    private final String stkPushUrl;
    private final String callBackUrl;
    private final DarajaAuthClient darajaAuthClient;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newHttpClient();

    public DarajaStkClient(@Value("${daraja.shortcode}") String shortCode,
                           @Value("${daraja.passkey}") String passKey,
                           @Value("${daraja.stk-push-url}") String stkPushUrl,
                           @Value("${daraja.callback-url}") String callBackUrl,
                           DarajaAuthClient darajaAuthClient,
                           ObjectMapper objectMapper) {
        this.shortCode = shortCode;
        this.passKey = passKey;
        this.stkPushUrl = stkPushUrl;
        this.callBackUrl = callBackUrl;
        this.darajaAuthClient = darajaAuthClient;
        this.objectMapper = objectMapper;
    }


    //Method to initiate stk push, it takes the transaction in the db with the status INITIATED
    public StkResult initiateStkPush(Transaction transaction) throws InterruptedException {
        String token = darajaAuthClient.getAccessToken();
        // Daraja validates the timestamp in Kenyan time, whatever zone the server runs in
        String timestamp = LocalDateTime.now(ZoneId.of("Africa/Nairobi"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = generatePassword(timestamp);


        Map<String, Object> stkRequest = new LinkedHashMap<>();
        stkRequest.put("BusinessShortCode", shortCode);
        stkRequest.put("Password", password);
        stkRequest.put("Timestamp", timestamp);
        stkRequest.put("TransactionType", "CustomerPayBillOnline");
        // M-Pesa takes whole shillings only, PaymentRequest rejects amounts with cents
        stkRequest.put("Amount", transaction.getAmount().intValueExact());
        stkRequest.put("PartyA", transaction.getPhoneNumber());
        stkRequest.put("PartyB", shortCode);
        stkRequest.put("PhoneNumber", transaction.getPhoneNumber());
        stkRequest.put("CallBackURL", callBackUrl);
        // The "account number" the customer sees on the prompt, max 12 characters
        stkRequest.put("AccountReference", transaction.getMeter().getMeterNumber());
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


            } catch (ConnectException | HttpConnectTimeoutException e){
                // We never reached Daraja, so retrying cannot send the customer a second prompt
                if (attempts < MAX_ATTEMPTS){
                    Thread.sleep(RETRY_DELAY_MS);
                    continue;
                }

                return StkResult.failure("Network failure after " + attempts + " attempts: " + e.getMessage());

            } catch (IOException e){
                // The request may have reached Daraja (e.g. timed out waiting for the reply), so a
                // retry could prompt the customer twice. Don't retry.
                return StkResult.failure("Network failure after sending STK push: " + e.getMessage());
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

        //Dara level
        // Did data accept the request
        if (!"0".equals(darajaResponse.ResponseCode())){
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
