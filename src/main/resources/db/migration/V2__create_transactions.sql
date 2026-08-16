CREATE TABLE transactions (
    id                    BIGSERIAL PRIMARY KEY,
    reference_number      VARCHAR(100)  NOT NULL,
    user_id               VARCHAR(255)  NOT NULL,
    package_id            VARCHAR(50)   NOT NULL,
    billing_cycle         VARCHAR(20)   NOT NULL,
    amount                NUMERIC(12,2) NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    paymongo_event_id     VARCHAR(255),
    checkout_session_id   VARCHAR(255),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_transactions_reference_number UNIQUE (reference_number),
    CONSTRAINT uq_transactions_paymongo_event_id UNIQUE (paymongo_event_id)
);

CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_status ON transactions (status);