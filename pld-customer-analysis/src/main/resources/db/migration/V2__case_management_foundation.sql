CREATE TABLE pld_case (
    id VARCHAR(32) PRIMARY KEY,
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    origin VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    grouping_policy_version VARCHAR(128) NOT NULL,
    source_count INTEGER NOT NULL,
    assigned_actor_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_pld_case_status_created_at ON pld_case (status, created_at);
CREATE INDEX idx_pld_case_party_status ON pld_case (party_id, status);

CREATE TABLE case_source (
    id VARCHAR(32) PRIMARY KEY,
    case_id VARCHAR(32) NOT NULL REFERENCES pld_case(id),
    source_system VARCHAR(128) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(128) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    reason_code VARCHAR(128) NOT NULL,
    grouping_policy_version VARCHAR(128) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    causation_id VARCHAR(128) NOT NULL,
    attached_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_case_source_origin UNIQUE (source_system, source_id, grouping_policy_version)
);

CREATE INDEX idx_case_source_case_attached_at ON case_source (case_id, attached_at);
