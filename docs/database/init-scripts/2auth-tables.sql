CREATE TABLE auth.users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    phone           VARCHAR(15) UNIQUE NOT NULL,
    role            VARCHAR(20) DEFAULT 'USER',
    is_active       BOOLEAN DEFAULT true,
    is_verified     BOOLEAN DEFAULT false,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE auth.refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) UNIQUE NOT NULL,
    device_info  VARCHAR(255),
    ip_address   VARCHAR(45),
    expires_at   TIMESTAMP NOT NULL,
    is_revoked   BOOLEAN DEFAULT false,
    created_at   TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_refresh_token_hash ON auth.refresh_tokens(token_hash);
CREATE INDEX idx_refresh_token_user ON auth.refresh_tokens(user_id);