CREATE TABLE integration_outbox (
    event_id VARCHAR(26) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    envelope JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_error TEXT,
    CONSTRAINT ux_integration_outbox_aggregate UNIQUE (event_type, aggregate_id)
);

CREATE INDEX ix_integration_outbox_pending
    ON integration_outbox (status, next_attempt_at, occurred_at);
