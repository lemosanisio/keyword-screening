CREATE TABLE party (
    id VARCHAR(32) PRIMARY KEY,
    party_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE party_snapshot (
    id VARCHAR(32) PRIMARY KEY,
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    snapshot_version INTEGER NOT NULL,
    official_name TEXT NOT NULL,
    source_system VARCHAR(128) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_party_snapshot_version UNIQUE (party_id, snapshot_version)
);

CREATE TABLE analysis_cycle (
    id VARCHAR(32) PRIMARY KEY,
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    cycle_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE timeline_entry (
    id VARCHAR(32) PRIMARY KEY,
    party_id VARCHAR(32) NOT NULL,
    analysis_cycle_id VARCHAR(32),
    entry_type VARCHAR(128) NOT NULL,
    business_occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    actor_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    summary_code VARCHAR(128) NOT NULL,
    object_type VARCHAR(128) NOT NULL,
    object_id VARCHAR(64) NOT NULL,
    object_version VARCHAR(64),
    correlation_id VARCHAR(128) NOT NULL,
    causation_id VARCHAR(128),
    visibility_classification VARCHAR(32) NOT NULL
);

CREATE INDEX idx_timeline_entry_party_recorded_at ON timeline_entry (party_id, recorded_at DESC);

CREATE TABLE outbox_event (
    id VARCHAR(32) PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL
);

CREATE INDEX idx_outbox_event_status_occurred_at ON outbox_event (status, occurred_at);

CREATE TABLE inbox_event (
    consumer_name VARCHAR(128) NOT NULL,
    event_id VARCHAR(32) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL,
    PRIMARY KEY (consumer_name, event_id)
);
