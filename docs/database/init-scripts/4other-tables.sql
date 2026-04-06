-- Transaction tables
CREATE TABLE transaction.ledger_entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_request_id  UUID NOT NULL,
    account_id          UUID NOT NULL,
    entry_type          VARCHAR(6) NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    balance_after       DECIMAL(15,2) NOT NULL,
    description         TEXT,
    payment_type        VARCHAR(10),
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE transaction.budgets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    category    VARCHAR(50) NOT NULL,
    amount      DECIMAL(15,2) NOT NULL,
    month       INTEGER NOT NULL,
    year        INTEGER NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, category, month, year)
);

-- Fraud tables
CREATE TABLE fraud.fraud_checks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_request_id  UUID NOT NULL,
    user_id             UUID NOT NULL,
    risk_score          INTEGER DEFAULT 0,
    decision            VARCHAR(20),
    rules_triggered     TEXT[],
    evaluated_at        TIMESTAMP DEFAULT NOW()
);

CREATE TABLE fraud.blacklisted_vpas (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vpa       VARCHAR(50) UNIQUE NOT NULL,
    reason    TEXT,
    added_at  TIMESTAMP DEFAULT NOW()
);

-- Notification tables
CREATE TABLE notification.notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    type        VARCHAR(30) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    channel     VARCHAR(10) NOT NULL,
    is_read     BOOLEAN DEFAULT false,
    sent_at     TIMESTAMP,
    read_at     TIMESTAMP,
    metadata    JSONB,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_notif_user ON notification.notifications(user_id, is_read);

-- MCP tables
CREATE TABLE mcp.ai_conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL,
    role        VARCHAR(15) NOT NULL,
    content     TEXT NOT NULL,
    tool_calls  JSONB,
    created_at  TIMESTAMP DEFAULT NOW()
);