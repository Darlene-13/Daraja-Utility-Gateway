# UTILITY PAYMENT PLATFORM

Pay for electricity and water tokens with M-Pesa. A customer gives a meter number and an
amount, approves an STK push on their phone, and the platform issues a token for that meter.

The hard part is not the payment, it is everything around it: Safaricom callbacks can
arrive late, twice, or never. The platform is built so that a customer who paid always
ends up with a token or with a person looking at their case, never silently stuck.

- **Design and decisions:** [UtilityPaymentPlatform/docs/Architecture.md](UtilityPaymentPlatform/docs/Architecture.md)
- **First live test against Daraja (what worked, with evidence):** [UtilityPaymentPlatform/docs/first-live-test.md](UtilityPaymentPlatform/docs/first-live-test.md)

## Status

Version 1 works end to end against the Daraja sandbox: STK push -> callback -> token in
about 13 seconds, with a real KES 1 payment.

| Built | Not built yet |
|---|---|
| Payment API with per-phone rate limiting and one-pending-payment guard | Token delivery to the customer (SMS, via a separate Go notification service) |
| STK push, retried only when Daraja was never reached | USSD channel for customers |
| Callback receiver -> Redis stream -> worker with dedupe, retries and dead-lettering | Real KPLC / water provider integration (tokens are simulated) |
| Transactional outbox -> Redis streams | Callback hardening (IP allowlist, secret path) |
| Token worker with idempotency key, retries and dead-lettering | Correlation id propagation through logs, PII masking in logs |
| Reconciliation job for callbacks that never arrive | Docker, OpenAPI docs |
| Dead-letter monitor that logs when anything needs a person | |

## How a payment flows

```
POST /api/v1/payment/
  -> rate limit -> pending check -> meter lookup -> save INITIATED -> STK push -> PENDING_CONFIRMATION
Safaricom -> POST /api/v1/callback/stk -> stream mpesa-callbacks
  -> CallBackWorker -> PAID or FAILED + outbox row (one DB transaction)
ReconciliationJob (every 60 s) -> asks Daraja about PENDING_CONFIRMATION older than 2 min -> same resolve path
OutboxPublisher (every 1 s) -> PAYMENT_CONFIRMED to payment-events, everything else to notification-events
TokenWorker <- payment-events -> issuer for the meter type -> TOKEN_ISSUED or FAILED_TOKEN_ISSUANCE
```

Transaction states:

```
INITIATED -> PENDING_CONFIRMATION -> PAID -> TOKEN_ISSUED
PENDING_CONFIRMATION -> FAILED
PAID -> FAILED_TOKEN_ISSUANCE (needs_review = true)
```

## Running it locally

### Prerequisites

- Java 21
- PostgreSQL and Redis running locally
- A Daraja sandbox app from [developer.safaricom.co.ke](https://developer.safaricom.co.ke) (consumer key and secret, sandbox passkey)
- [ngrok](https://ngrok.com), so Safaricom can reach your callback endpoint

### 1. Database

```bash
sudo -u postgres psql -c "CREATE ROLE utility LOGIN PASSWORD 'choose-a-password';"
sudo -u postgres psql -c "CREATE DATABASE utility_payments OWNER utility;"
```

Flyway creates the tables on startup from `src/main/resources/db/migration`.

### 2. Callback tunnel

```bash
ngrok http 8080
```

Copy the `https://….ngrok-free.dev` address. The ngrok inspector at
http://127.0.0.1:4040 shows every callback Safaricom sends, which is the easiest way to
see what is happening.

### 3. Environment

```bash
cd UtilityPaymentPlatform
cp .env.example .env
```

Fill in `.env`:

| Variable | Value |
|---|---|
| `DARAJA_CONSUMER_KEY`, `DARAJA_CONSUMER_SECRET` | From your Daraja app |
| `DARAJA_SHORTCODE` | `174379` for the sandbox |
| `DARAJA_PASSKEY` | Sandbox passkey from the Daraja STK push simulator page |
| `DARAJA_CALLBACK_URL` | `https://<your-ngrok-address>/api/v1/callback/stk` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Optional, default to `localhost:5432/utility_payments`, user `utility` |

`.env` is git-ignored. Spring reads it through `spring.config.import`, so **start the
app from the `UtilityPaymentPlatform` directory** or the file will not be found.

### 4. Start

```bash
./mvnw spring-boot:run
```

### 5. Add a customer and meter

There is no admin API yet, so seed them directly:

```sql
WITH c AS (
  INSERT INTO customers (full_name, phone_number)
  VALUES ('Test Customer', '2547XXXXXXXX')
  RETURNING id
)
INSERT INTO meters (meter_number, meter_type, customer_id)
SELECT 'MTR-0001', 'ELECTRICITY', id FROM c;
```

If you use an IDE database console, make sure it auto-commits, or run `COMMIT;`.

```bash
curl -s http://localhost:8080/api/v1/meters/MTR-0001
```

## Making a payment

Phone numbers use the `2547XXXXXXXX` or `2541XXXXXXXX` format. Amounts are whole
shillings, minimum 1. Note the trailing slash on the URL.

```bash
curl -s -X POST http://localhost:8080/api/v1/payment/ \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"2547XXXXXXXX","meterNumber":"MTR-0001","amount":1}'
```

```json
{"transactionId":"…","status":"PENDING_CONFIRMATION","checkoutRequestId":"ws_CO_…","message":null}
```

Approve the prompt on the phone, then check the payment:

```bash
curl -s http://localhost:8080/api/v1/payment/<transactionId>
```

The token is not in the API response yet. To see it:

```sql
SELECT status, mpesa_receipt_number, token, token_units
FROM transactions WHERE id = '<transactionId>';
```

| Response | Meaning |
|---|---|
| `202` | STK push sent, waiting for the customer |
| `400` | Invalid phone number, meter number or amount |
| `403` | Could not authenticate with Daraja (check the consumer key and secret) |
| `404` | Meter does not exist |
| `429` | More than 3 attempts from this phone in a minute, or a payment for this phone is already pending |

### Sandbox notes

- The prompt goes to a real phone and **real money can be deducted**. Use KES 1.
- Prompts on shortcode `174379` sometimes never arrive. `ResultCode 1037` means
  Safaricom could not reach the phone or the customer did not answer in time.
- Callbacks are sometimes never sent, even for successful payments. The reconciliation
  job resolves those after about 2 minutes. Payments resolved that way have no M-Pesa
  receipt number, because Daraja's status query does not return one.
- If the customer has too little balance, a Fuliza screen appears before the PIN and can
  use up the prompt's time limit.

## Configuration

All in `UtilityPaymentPlatform/src/main/resources/application.properties`.

| Setting | Default | What it does |
|---|---|---|
| `rate-limit.payment.limit` / `window-ms` | 3 / 60000 | Payment attempts allowed per phone number per window |
| `reconciliation.pending-after-ms` | 120000 | How long to wait for a callback before asking Daraja |
| `reconciliation.give-up-after-ms` | 1800000 | After this, flag the transaction `needs_review` |
| `callback.max-attempts`, `token.max-attempts` | 5 | Deliveries before a message goes to its dead-letter stream |
| `electricity.api.base-url`, `water.api.base-url` | blank | Blank uses `SimulatedTokenIssuer` for that meter type |
| `token.simulated.failure-rate` | 0.0 | Share of simulated vend calls that fail, to exercise retries |

## Operations

### Where problems end up

| Where | What it means |
|---|---|
| `transactions.needs_review = true` | A person must decide: vend by hand, refund, or replay |
| Redis `mpesa-callbacks:dead` | A callback that could not be parsed or kept failing |
| Redis `payment-events:token-dead` | A paid transaction whose token could not be issued |

`DeadLetterMonitor` logs an `ERROR` each time any of these grows. Point log alerting at it.

```bash
redis-cli xlen payment-events:token-dead
redis-cli xrange payment-events:token-dead - +
```

### Replaying a dead-lettered payment

Use this once the cause is fixed, for example when the token provider is back. The
customer does not pay again: the replay only reruns token issuing, and the provider
receives the same idempotency key, so it cannot create a second token.

```sql
UPDATE transactions
SET status = 'PAID', failure_reason = NULL, needs_review = FALSE
WHERE id = '<transactionId>' AND status = 'FAILED_TOKEN_ISSUANCE';
```

```bash
# copy the fields of the entry shown by xrange above
redis-cli xadd payment-events '*' outboxId <outboxId> eventType PAYMENT_CONFIRMED \
  aggregateId <transactionId> payload '<payload json>'

# once the transaction shows TOKEN_ISSUED, remove the dead-letter entry
redis-cli xdel payment-events:token-dead <entry-id>
```
