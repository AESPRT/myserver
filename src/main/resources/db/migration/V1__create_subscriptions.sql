CREATE TABLE subscriptions (
    id                BIGSERIAL PRIMARY KEY,
    user_id           VARCHAR(255)  NOT NULL,
    package_id        VARCHAR(50)   NOT NULL,
    billing_cycle     VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_subscriptions_user_id UNIQUE (user_id)
);

CREATE INDEX idx_subscriptions_status ON subscriptions (status);