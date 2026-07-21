CREATE TABLE case_comment (
    id VARCHAR(32) PRIMARY KEY,
    case_id VARCHAR(32) NOT NULL REFERENCES pld_case(id),
    party_id VARCHAR(32) NOT NULL REFERENCES party(id),
    body TEXT NOT NULL,
    created_by_actor_id VARCHAR(128) NOT NULL,
    created_by_actor_role VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_case_comment_case_created_at ON case_comment (case_id, created_at);
