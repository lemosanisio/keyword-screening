CREATE TABLE transaction_identity (
    id UUID PRIMARY KEY,
    source_system VARCHAR(100) NOT NULL,
    external_transaction_id VARCHAR(100) NOT NULL,
    transaction_id VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_transaction_identity_source_external UNIQUE (source_system, external_transaction_id),
    CONSTRAINT ux_transaction_identity_internal UNIQUE (transaction_id)
);

CREATE TABLE transaction_evaluation (
    evaluation_id VARCHAR(30) PRIMARY KEY,
    decision_execution_id UUID NOT NULL REFERENCES decision_execution(id),
    transaction_id VARCHAR(30) NOT NULL,
    source_system VARCHAR(100) NOT NULL,
    external_transaction_id VARCHAR(100) NOT NULL,
    transaction_version INTEGER NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    evaluation_request_id VARCHAR(100),
    input_event_id VARCHAR(26) NOT NULL,
    input_event_schema_version INTEGER NOT NULL,
    snapshot JSONB NOT NULL,
    snapshot_ref VARCHAR(100) NOT NULL,
    snapshot_format_version VARCHAR(50) NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    ruleset_version VARCHAR(100) NOT NULL,
    risk_context JSONB NOT NULL,
    facts JSONB NOT NULL,
    rules_executed JSONB NOT NULL,
    rules_triggered JSONB NOT NULL,
    execution_status VARCHAR(30) NOT NULL,
    evaluation_outcome VARCHAR(30),
    review_required BOOLEAN,
    recommended_route VARCHAR(50),
    explanation JSONB NOT NULL,
    party_id VARCHAR(30),
    correlation_id VARCHAR(128) NOT NULL,
    causation_id VARCHAR(128),
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_transaction_evaluation_execution UNIQUE (decision_execution_id),
    CONSTRAINT ck_transaction_evaluation_hash CHECK (snapshot_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_transaction_evaluation_versions CHECK (transaction_version >= 1 AND input_event_schema_version >= 1),
    CONSTRAINT ck_transaction_evaluation_purpose CHECK (purpose IN ('LIVE', 'REPLAY', 'BACKTEST', 'DRY_RUN', 'INVESTIGATION'))
);

CREATE UNIQUE INDEX ux_transaction_evaluation_live
    ON transaction_evaluation (transaction_id, transaction_version, ruleset_version, purpose)
    WHERE purpose = 'LIVE';

CREATE INDEX ix_transaction_evaluation_external
    ON transaction_evaluation (external_transaction_id, evaluated_at DESC);

CREATE UNIQUE INDEX ux_transaction_evaluation_non_live_request
    ON transaction_evaluation (evaluation_request_id, purpose)
    WHERE purpose <> 'LIVE';

CREATE UNIQUE INDEX ux_transaction_evaluation_input
    ON transaction_evaluation (input_event_id, purpose);
ALTER TABLE decision_execution
    DROP CONSTRAINT uk_decision_execution_idempotency,
    ADD COLUMN fact_results JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN evaluation_status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN evaluation_outcome VARCHAR(30) NOT NULL DEFAULT 'NO_SIGNAL',
    ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN recommended_route VARCHAR(50);

UPDATE decision_execution
SET evaluation_outcome = CASE
        WHEN actions ? 'GENERATE_ALERT' THEN 'SIGNAL_RAISED'
        ELSE 'NO_SIGNAL'
    END,
    review_required = actions ? 'REVIEW',
    recommended_route = CASE
        WHEN actions ? 'REVIEW' THEN 'DERIVED_TO_ANALYST'
        ELSE NULL
    END;

CREATE TABLE screening_intake (
    transaction_id VARCHAR(100) PRIMARY KEY,
    payload_hash VARCHAR(64) NOT NULL,
    input_event_id VARCHAR(26) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_screening_intake_hash CHECK (payload_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ux_screening_intake_event UNIQUE (input_event_id)
);
