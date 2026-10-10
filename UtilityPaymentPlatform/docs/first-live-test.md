# First Live Test: Daraja Sandbox, 10 and 11 October 2026

This records the first end-to-end run of the platform against Safaricom's Daraja sandbox
with real phones. The goal was to confirm that a customer can pay by STK push and get a
token back, and that every failure path we designed for behaves as intended.

**Result:** a real KES 1 payment went from STK push to issued token in **13 seconds**,
with no manual steps. Along the way the failed, timed-out, lost-callback and
token-failure paths were all exercised against live traffic. On 11 October a USSD menu
through the Africa's Talking sandbox was added and reached Daraja through the same flow.

Phone numbers are masked as `2547XXXXX123`. Daraja's `CheckoutRequestID` ends with the
customer's phone number, so those are masked the same way.

## Setup used

| Piece | Value |
|---|---|
| Daraja environment | Sandbox, shortcode `174379` |
| Callback URL | ngrok tunnel to `localhost:8080/api/v1/callback/stk` |
| Postgres | local, schema created by Flyway `V1__init.sql` |
| Redis | local, streams `mpesa-callbacks`, `payment-events`, `notification-events` |
| Token provider | `SimulatedTokenIssuer` (no real KPLC or water API yet) |
| Test meters | `MTR-0001` and `MTR-0002`, both `ELECTRICITY` |

> The sandbox still sends the prompt to a real handset, and in this test **KES 1 was
> actually deducted** from the paying phone. Keep test amounts at KES 1.

## Headline result: the happy path

```bash
curl -s -X POST http://localhost:8080/api/v1/payment/ \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"2547XXXXX706","meterNumber":"MTR-0002","amount":1}'
```

The customer entered their PIN, and Safaricom posted this callback to the ngrok URL:

```json
{
  "Body": {
    "stkCallback": {
      "MerchantRequestID": "f57c-4d7a-b000-6a48cacb5dd71056",
      "CheckoutRequestID": "ws_CO_1010202622584627XXXXX706",
      "ResultCode": 0,
      "ResultDesc": "The service request is processed successfully.",
      "CallbackMetadata": {
        "Item": [
          { "Name": "Amount", "Value": 1 },
          { "Name": "MpesaReceiptNumber", "Value": "UJACS970KG" },
          { "Name": "Balance" },
          { "Name": "TransactionDate", "Value": 20261010225854 },
          { "Name": "PhoneNumber", "Value": 2547XXXXX706 }
        ]
      }
    }
  }
}
```

Our endpoint answered `200` with `{"ResultCode":0,"ResultDesc":"Accepted"}`.

### Timeline

| Time (EAT) | What happened |
|---|---|
| 22:58:44 | `POST /api/v1/payment/` -> transaction saved as `INITIATED`, STK push sent, status `PENDING_CONFIRMATION`, `202 Accepted` returned |
| 22:58:54 | Customer enters PIN (Safaricom's `TransactionDate`) |
| 22:58:56 | Callback arrives -> queued on `mpesa-callbacks` -> `CallBackWorker` sets status `PAID`, stores receipt `UJACS970KG`, writes `PAYMENT_CONFIRMED` to the outbox |
| 22:58:57 | `OutboxPublisher` puts the event on `payment-events` -> `TokenWorker` issues the token -> status `TOKEN_ISSUED`, `TOKEN_ISSUED` event written to the outbox |
| 22:58:58 | `TOKEN_ISSUED` published to `notification-events`, ready for the notification service |

### Final state of the transaction

```
status:                    TOKEN_ISSUED
amount:                    1.00
mpesa_receipt_number:      UJACS970KG
token:                     57706278834966293229
token_units:               0.04          (simulated tariff: KES 25 per kWh)
token_provider_reference:  SIM-ELECTRICITY-ws_CO_1010202622584627XXXXX706
needs_review:              false
```

Outbox history for this payment: `PAYMENT_CONFIRMED` -> `TOKEN_ISSUED`, both `PUBLISHED`.

Against the SLAs in [Architecture.md](Architecture.md): callback processing took about
1 second (target under 5) and token issuance took about 1 second after confirmation
(target under 10).

## Failure paths seen during the test

Before the happy path worked, the earlier attempts exercised almost every failure branch.

| # | Time sent | What the customer saw | How the result reached us | Final state |
|---|---|---|---|---|
| 1 | 22:10:58 | No prompt | Callback at 22:11:27, `ResultCode 1037` "No response from user." | `FAILED` |
| 2 | 22:11:37 | No prompt | **No callback ever arrived.** `ReconciliationJob` queried Daraja at 22:13:48: "DS timeout user cannot be reached." | `FAILED` |
| 3 | 22:16:27 | No prompt | Callback at 22:16:54, `ResultCode 1037` | `FAILED` |
| 4 | 22:17:22 | Fuliza consent screen, then no PIN screen | No callback. Reconciliation at 22:19:50: "No response from user." | `FAILED` |
| 5 | 22:28:10 | Prompt, PIN entered, **KES 1 deducted** | **No callback ever arrived.** Reconciliation at 22:30:51 found it paid | `PAID` -> see below |

Every `FAILED` transaction got a `PAYMENT_FAILED` outbox event, published to
`notification-events` so the customer can be told.

**What we learned about the sandbox:** prompts on shortcode `174379` are unreliable, and
callbacks are sometimes never sent at all, even for a successful payment (attempt 5).
Without `ReconciliationJob`, attempt 5 would have stayed `PENDING_CONFIRMATION`
forever: a customer who paid and got nothing. A Fuliza prompt adds a screen before the PIN
and can use up the prompt's time limit.

### Attempt 5: paid, but the token could not be issued, then recovered

This attempt ran before a token provider was configured, so it exercised the token
failure path from start to finish:

1. Reconciliation marked it `PAID` and wrote `PAYMENT_CONFIRMED`. No receipt number
   is stored, because Daraja's status query does not return one.
2. `TokenWorker` tried to vend, failed (no provider URL), and left the message pending.
   `reclaimStuck()` retried it.
3. After 5 deliveries it gave up: status `FAILED_TOKEN_ISSUANCE`, `needs_review = true`,
   a `TOKEN_ISSUANCE_FAILED` event for the customer, and the message moved to
   `payment-events:token-dead`.
4. We added `SimulatedTokenIssuerConfig`, restarted, and **replayed** the dead-lettered
   event (steps in the [README](../../README.md#replaying-a-dead-lettered-payment)).
5. The worker issued token `48534599188842482400` within a second: status `TOKEN_ISSUED`.

Outbox history: `PAYMENT_CONFIRMED` -> `TOKEN_ISSUANCE_FAILED` -> `TOKEN_ISSUED`.
The customer paid once and got exactly one token. Nobody had to send a second STK push.

## Rate limiter

Five requests in quick succession from one phone number. A meter that does not exist
was used on purpose: the limiter runs before any other check, so this proves it without
calling Daraja or charging anyone.

```
call 1: 404  No meter found with the number: MTR-9999
call 2: 404  No meter found with the number: MTR-9999
call 3: 404  No meter found with the number: MTR-9999
call 4: 429  Too many payment attempts, please wait before trying again
call 5: 429  Too many payment attempts, please wait before trying again
```

Configured as 3 attempts per phone number per 60 seconds (`rate-limit.payment.*`).

## Bugs found and fixed during the test

| Problem | Fix |
|---|---|
| `GET /api/v1/meters/{n}` returned 404 even for an existing meter: `@Controller` treated the result as a view name | Changed to `@RestController` |
| A paid payment could never get a token: both provider URLs were blank and nothing replaced them | `SimulatedTokenIssuerConfig` registers `SimulatedTokenIssuer` for any meter type without a provider URL |


## USSD channel via Africa's Talking (11 October)

A USSD menu was added as a second way in (`POST /api/v1/ussd`, sandbox code
`*384*35132#`). It calls the same `InitiatePaymentUseCase` as the REST API.

### Menu checks (curl, no payment sent)

| `text` sent | Reply |
|---|---|
| `""` | `CON Pay for: 1. Electricity 2. Water` |
| `3` | `END Invalid choice. Please dial again and choose 1 or 2.` |
| `1` | `CON Enter meter number` |
| `1*MTR-0002` | `CON Enter amount (KES)` |
| `2*MTR-0002` | `END Meter MTR-0002 is not a water meter.` |
| `1*MTR-9999` | `END Meter MTR-9999 was not found.` |
| `1*MTR-0002*abc` | `END Invalid amount. Enter whole shillings, at least 1.` |
| `1*MTR-0002*0` | `END Invalid amount. Enter whole shillings, at least 1.` |

All returned HTTP 200, which is what AT expects even for error screens.

### Full session from the AT simulator

The ngrok inspector showed one request from AT per key press, all in one session
(`ATUid_a8b0...`):

| Time (EAT) | `text` | Our reply |
|---|---|---|
| 00:22:41 | `""` | Main menu |
| 00:22:45 | `1` | Enter meter number |
| 00:22:49 | `1*MTR-0002` | Enter amount |
| 00:22:53 | `1*MTR-0002*1` | `END Check your phone for the M-Pesa prompt to pay KES 1...` |

The phone number arrived as `+2547XXXXX706` and was normalised to `2547XXXXX706`.
A transaction was created and **Daraja accepted the STK push** (CheckoutRequestID
`ws_CO_1110202600225647XXXXX706`), so USSD to Daraja works.

**The prompt never reached the phone,** and no callback arrived. At the time of writing
(00:31) the transaction was still `PENDING_CONFIRMATION`: the reconciliation job had not
resolved it, most likely because Daraja's status query kept answering that the request
was still being processed, which the job deliberately leaves for the next round. If that
continues, the job flags it `needs_review` after 30 minutes.

Two possible causes, not yet told apart:
- The same sandbox unreliability seen in attempts 1 to 4 above.
- The STK push is sent before the `END` reply, while the USSD session is still open. On
  some phones the prompt cannot appear during a USSD session. The fix is to reply `END`
  first and start the payment asynchronously (README, Next steps).

## Not covered yet

- **Customer cannot see the token.** It is stored on the transaction, but
  `GET /api/v1/payment/{id}` does not return it and no SMS is sent yet. Delivery comes
  with the notification service (Go), sending SMS through Africa's Talking.
- **A USSD payment through to a token.** The USSD path reached Daraja but the one live
  attempt got no prompt (see above).
- **Real token providers.** Tokens come from `SimulatedTokenIssuer`.
- **Load and concurrency.** One payment at a time was tested.
- **Callback hardening** described in Architecture.md (IP allowlist, secret in the
  callback path) is not built yet.
