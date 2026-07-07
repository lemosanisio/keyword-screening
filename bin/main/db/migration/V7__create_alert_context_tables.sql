-- Alert Context: tabela de alertas gerados pelo Decision Engine
-- Requirements: 12.2, 12.4, 12.8
CREATE TABLE IF NOT EXISTS alert (
    id                      UUID            PRIMARY KEY,
    transaction_id          VARCHAR(64)     NOT NULL,
    rule_id                 UUID            NOT NULL,
    customer_id             VARCHAR(64)     NOT NULL,
    facts                   JSONB,
    configuration_version   INTEGER,
    trace_id                VARCHAR(128),
    actions                 JSONB,
    explanation             JSONB,
    status                  VARCHAR(32)     NOT NULL DEFAULT 'OPEN',
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_alert_idempotency UNIQUE (transaction_id, rule_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_transaction_id ON alert(transaction_id);
CREATE INDEX IF NOT EXISTS idx_alert_rule_id ON alert(rule_id);
CREATE INDEX IF NOT EXISTS idx_alert_status ON alert(status);
CREATE INDEX IF NOT EXISTS idx_alert_customer_id ON alert(customer_id);
