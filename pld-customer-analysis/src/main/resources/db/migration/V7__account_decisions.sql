CREATE TABLE account_decision (
    id VARCHAR(32) PRIMARY KEY,
    case_id VARCHAR(32) NOT NULL REFERENCES pld_case(id),
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    decision VARCHAR(64) NOT NULL,
    decision_version INTEGER NOT NULL,
    reason_codes JSONB NOT NULL,
    narrative TEXT NOT NULL,
    policy_version VARCHAR(128) NOT NULL,
    decided_by_actor_id VARCHAR(128) NOT NULL,
    decided_by_actor_role VARCHAR(64) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    previous_decision_id VARCHAR(32),
    CONSTRAINT uq_account_decision_case_version UNIQUE (case_id, decision_version)
);

CREATE INDEX idx_account_decision_case_decided_at ON account_decision (case_id, decided_at);
