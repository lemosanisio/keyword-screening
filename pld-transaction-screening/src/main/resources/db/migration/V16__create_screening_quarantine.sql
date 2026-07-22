-- Quarentena para intakes inválidos (identidade instável ou snapshot inválido).
-- Nenhum TransactionEvaluation ou evento operacional é criado; o registro persiste
-- para auditoria e reprocessamento posterior.
CREATE TABLE screening_quarantine (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_system   VARCHAR(50)  NOT NULL,
    external_id     VARCHAR(255),
    reason_code     VARCHAR(80)  NOT NULL,
    reason_detail   TEXT,
    purpose         VARCHAR(20)  NOT NULL DEFAULT 'LIVE',
    raw_payload     JSONB        NOT NULL,
    correlation_id  VARCHAR(128),
    received_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_quarantine_reason ON screening_quarantine (reason_code);
CREATE INDEX idx_quarantine_received ON screening_quarantine (received_at);
