CREATE TABLE evidence_collection (
    id VARCHAR(32) PRIMARY KEY,
    case_id VARCHAR(32) NOT NULL REFERENCES pld_case(id),
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    analysis_cycle_id VARCHAR(32) NOT NULL,
    scenario VARCHAR(64) NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    revision INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_evidence_collection_case UNIQUE (case_id)
);

CREATE TABLE analysis_requirement (
    id VARCHAR(32) PRIMARY KEY,
    evidence_collection_id VARCHAR(32) NOT NULL REFERENCES evidence_collection(id),
    code VARCHAR(128) NOT NULL,
    title TEXT NOT NULL,
    category VARCHAR(64) NOT NULL,
    mandatory BOOLEAN NOT NULL,
    outcome VARCHAR(64) NOT NULL,
    outcome_reason VARCHAR(128) NOT NULL,
    display_order INTEGER NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_analysis_requirement_code UNIQUE (evidence_collection_id, code)
);

CREATE TABLE source_execution (
    id VARCHAR(32) PRIMARY KEY,
    evidence_collection_id VARCHAR(32) NOT NULL REFERENCES evidence_collection(id),
    requirement_id VARCHAR(32) NOT NULL REFERENCES analysis_requirement(id),
    source_code VARCHAR(128) NOT NULL,
    source_name TEXT NOT NULL,
    attempt INTEGER NOT NULL,
    status VARCHAR(64) NOT NULL,
    adapter_version VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    summary TEXT NOT NULL,
    error_code VARCHAR(128),
    correlation_id VARCHAR(128) NOT NULL,
    CONSTRAINT uq_source_execution_attempt UNIQUE (requirement_id, source_code, attempt)
);

CREATE TABLE evidence_record (
    id VARCHAR(32) PRIMARY KEY,
    source_execution_id VARCHAR(32) NOT NULL REFERENCES source_execution(id),
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    analysis_cycle_id VARCHAR(32) NOT NULL,
    evidence_type VARCHAR(64) NOT NULL,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ,
    reference_key VARCHAR(128) NOT NULL,
    integrity_hash VARCHAR(128) NOT NULL,
    classification VARCHAR(32) NOT NULL,
    structured_data JSONB NOT NULL
);

CREATE TABLE fact_version (
    id VARCHAR(32) PRIMARY KEY,
    evidence_id VARCHAR(32) NOT NULL REFERENCES evidence_record(id),
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    analysis_cycle_id VARCHAR(32) NOT NULL,
    fact_code VARCHAR(128) NOT NULL,
    label TEXT NOT NULL,
    value JSONB NOT NULL,
    quality VARCHAR(32) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ
);

CREATE INDEX idx_evidence_collection_case ON evidence_collection (case_id);
CREATE INDEX idx_analysis_requirement_collection ON analysis_requirement (evidence_collection_id, display_order);
CREATE INDEX idx_source_execution_requirement ON source_execution (requirement_id, attempt);
CREATE INDEX idx_evidence_record_execution ON evidence_record (source_execution_id);
CREATE INDEX idx_fact_version_evidence ON fact_version (evidence_id);
