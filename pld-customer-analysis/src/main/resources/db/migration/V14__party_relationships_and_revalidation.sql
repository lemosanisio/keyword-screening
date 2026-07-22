-- Relações entre parties (PF/PJ)
CREATE TABLE party_relationship (
    id                      VARCHAR(30)  PRIMARY KEY,
    from_party_id           VARCHAR(30)  NOT NULL,
    to_party_id             VARCHAR(30)  NOT NULL,
    relationship_type       VARCHAR(50)  NOT NULL,
    participation_percentage DECIMAL(5,2),
    start_date              DATE,
    end_date                DATE,
    source_system           VARCHAR(50)  NOT NULL,
    source_event_id         VARCHAR(30),
    created_at              TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_relationship_from ON party_relationship (from_party_id);
CREATE INDEX idx_relationship_to ON party_relationship (to_party_id);

-- Política de revalidação por risco
CREATE TABLE revalidation_policy (
    risk_level              VARCHAR(20)  PRIMARY KEY,
    review_interval_days    INT          NOT NULL
);

INSERT INTO revalidation_policy (risk_level, review_interval_days) VALUES
    ('LOW', 365),
    ('MEDIUM', 180),
    ('HIGH', 90);

-- Registro de última revisão completada por party
ALTER TABLE party ADD COLUMN IF NOT EXISTS last_review_completed_at TIMESTAMP;
ALTER TABLE party ADD COLUMN IF NOT EXISTS current_risk_level VARCHAR(20) DEFAULT 'LOW';
