CREATE TABLE processed_webhook_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(100) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_processed_webhook_events_event_id UNIQUE (event_id)
);

CREATE INDEX idx_processed_webhook_events_processed_at
    ON processed_webhook_events (processed_at);