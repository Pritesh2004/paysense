CREATE TABLE payment.accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE,
    account_number  VARCHAR(16) UNIQUE NOT NULL,
    ifsc_code       VARCHAR(11) DEFAULT 'PAYS0000001',
    balance         DECIMAL(15,2) DEFAULT 0.00,
    account_type    VARCHAR(20) DEFAULT 'SAVINGS',
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    version         BIGINT DEFAULT 0,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    CONSTRAINT balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE payment.wallets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE,
    balance         DECIMAL(15,2) DEFAULT 0.00,
    daily_limit     DECIMAL(15,2) DEFAULT 10000.00,
    today_spent     DECIMAL(15,2) DEFAULT 0.00,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    version         BIGINT DEFAULT 0,
    last_reset_date DATE DEFAULT CURRENT_DATE,
    CONSTRAINT wallet_balance_check CHECK (balance >= 0)
);

CREATE TABLE payment.vpa_registry (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vpa         VARCHAR(50) UNIQUE NOT NULL,
    account_id  UUID NOT NULL REFERENCES payment.accounts(id),
    is_primary  BOOLEAN DEFAULT true,
    is_active   BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE payment.payment_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(64) UNIQUE NOT NULL,
    sender_account_id   UUID REFERENCES payment.accounts(id),
    receiver_vpa        VARCHAR(50),
    receiver_account_no VARCHAR(16),
    receiver_ifsc       VARCHAR(11),
    amount              DECIMAL(15,2) NOT NULL,
    payment_type        VARCHAR(10) NOT NULL,
    status              VARCHAR(20) DEFAULT 'PENDING',
    failure_reason      TEXT,
    utr_number          VARCHAR(22),
    description         VARCHAR(255),
    initiated_at        TIMESTAMP DEFAULT NOW(),
    settled_at          TIMESTAMP,
    metadata            JSONB
);

CREATE INDEX idx_payment_sender  ON payment.payment_requests(sender_account_id);
CREATE INDEX idx_payment_status  ON payment.payment_requests(status);
CREATE INDEX idx_payment_idem    ON payment.payment_requests(idempotency_key);