CREATE TABLE transaction_evaluation_projection (
    source_system VARCHAR(128) NOT NULL,
    evaluation_id VARCHAR(30) NOT NULL,
    event_id VARCHAR(26) NOT NULL,
    party_id VARCHAR(32),
    transaction_id VARCHAR(30) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    execution_status VARCHAR(30) NOT NULL,
    evaluation_outcome VARCHAR(30),
    review_required BOOLEAN,
    recommended_route VARCHAR(64),
    snapshot_ref TEXT NOT NULL,
    snapshot_format_version VARCHAR(64) NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    ruleset_version TEXT NOT NULL,
    facts JSONB NOT NULL,
    rules_executed JSONB NOT NULL,
    rules_triggered JSONB NOT NULL,
    explanation JSONB NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    PRIMARY KEY (source_system, evaluation_id),
    CONSTRAINT ux_transaction_evaluation_projection_event UNIQUE (source_system, event_id)
);

CREATE TABLE transaction_signal_projection (
    source_system VARCHAR(128) NOT NULL,
    signal_id VARCHAR(30) NOT NULL,
    event_id VARCHAR(26) NOT NULL,
    evaluation_id VARCHAR(30) NOT NULL,
    party_id VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(30) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (source_system, signal_id),
    CONSTRAINT ux_transaction_signal_projection_event UNIQUE (source_system, event_id)
);

CREATE TABLE manual_review_request (
    source_system VARCHAR(128) NOT NULL,
    source_request_id VARCHAR(30) NOT NULL,
    event_id VARCHAR(26) NOT NULL,
    evaluation_id VARCHAR(30) NOT NULL,
    party_id VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(30) NOT NULL,
    signal_ids JSONB NOT NULL,
    reason_codes JSONB NOT NULL,
    recommended_route VARCHAR(64) NOT NULL,
    grouping_policy_version_applied VARCHAR(128) NOT NULL,
    trigger_mode VARCHAR(32) NOT NULL,
    effect_status VARCHAR(32) NOT NULL,
    association_complete BOOLEAN NOT NULL DEFAULT FALSE,
    case_id VARCHAR(32),
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (source_system, source_request_id),
    CONSTRAINT ux_manual_review_request_event UNIQUE (source_system, event_id),
    CONSTRAINT ux_manual_review_request_evaluation UNIQUE (source_system, evaluation_id)
);

CREATE INDEX ix_transaction_signal_projection_evaluation
    ON transaction_signal_projection (source_system, evaluation_id);

CREATE INDEX ix_manual_review_request_evaluation
    ON manual_review_request (source_system, evaluation_id);
