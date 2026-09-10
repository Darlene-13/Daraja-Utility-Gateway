CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE customers (
                           id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           full_name       VARCHAR(255) NOT NULL,
                           phone_number    VARCHAR(15)  NOT NULL UNIQUE,
                           created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE meters (
                        id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        meter_number    VARCHAR(50)  NOT NULL UNIQUE,
                        meter_type      VARCHAR(20)  NOT NULL CHECK (meter_type IN ('WATER', 'ELECTRICITY')),
                        customer_id     UUID         NOT NULL REFERENCES customers(id),
                        created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_meters_customer_id ON meters(customer_id);

CREATE TABLE transactions (
                              id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              meter_id              UUID          NOT NULL REFERENCES meters(id),
                              customer_id           UUID          NOT NULL REFERENCES customers(id),
                              amount                NUMERIC(12,2) NOT NULL,
                              phone_number          VARCHAR(15)   NOT NULL,
                              status                VARCHAR(30)   NOT NULL CHECK (status IN (
                                                                                             'INITIATED', 'PENDING_CONFIRMATION', 'PAID',
                                                                                             'FAILED', 'TOKEN_ISSUED', 'FAILED_TOKEN_ISSUANCE'
                                  )),
                              merchant_request_id  VARCHAR(50),
                              checkout_request_id  VARCHAR(50) UNIQUE,
                              correlation_id       UUID          NOT NULL,
                              token                VARCHAR(50),
                              failure_reason       VARCHAR(255),
                              version              INTEGER       NOT NULL DEFAULT 0,
                              created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
                              updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Backs the reconciliation job's query for stuck PENDING_CONFIRMATION rows.
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_customer_id ON transactions(customer_id);

CREATE TABLE callback_logs (
                               id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- This unique constraint is the idempotency guard: a retried Safaricom
    -- callback fails to insert here, and I return success without
    -- reprocessing it.
                               checkout_request_id  VARCHAR(50) NOT NULL UNIQUE,
                               raw_payload          JSONB       NOT NULL,
                               received_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
                               id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               aggregate_type    VARCHAR(50) NOT NULL,
                               aggregate_id      UUID        NOT NULL,
                               event_type        VARCHAR(50) NOT NULL,
                               payload           JSONB       NOT NULL,
                               status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
                               created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
                               published_at      TIMESTAMPTZ
);

-- Backs the outbox publisher's SELECT ... FOR UPDATE SKIP LOCKED poll.
CREATE INDEX idx_outbox_status_created_at ON outbox_events(status, created_at);


-- Where is the FAILED_TOKEN_ISSUANCE AND status done...like I can't see that row and the one for needs review.'