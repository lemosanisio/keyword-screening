-- Auditoria completa de avaliações do Contextual Screening
-- Requirements: 7.1, 7.2, 9.3
CREATE TABLE contextual_screening_audit (
    id                   BIGSERIAL      PRIMARY KEY,
    transaction_id       VARCHAR(100)   NOT NULL,
    rule_id              VARCHAR(50)    NOT NULL,
    keyword              VARCHAR(255)   NOT NULL,
    prompt               TEXT           NOT NULL,
    model_response       JSONB,
    llm_classification   VARCHAR(50),
    llm_confidence       DECIMAL(4,3),
    final_classification VARCHAR(50)    NOT NULL,
    final_confidence     DECIMAL(4,3)   NOT NULL,
    requires_analyst_review BOOLEAN     NOT NULL,
    reason               TEXT           NOT NULL,
    analyst_decision     VARCHAR(50),
    created_at           TIMESTAMP      NOT NULL,
    CONSTRAINT uk_ctx_audit_tx_rule UNIQUE(transaction_id, rule_id)
);

CREATE INDEX idx_ctx_audit_tx_rule ON contextual_screening_audit(transaction_id, rule_id);
CREATE INDEX idx_ctx_audit_keyword ON contextual_screening_audit(keyword);
