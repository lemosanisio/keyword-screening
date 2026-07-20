-- Execuções persistidas das regras de screening
-- Requirements: 5.6
CREATE TABLE rule_execution (
    id             BIGSERIAL    PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    rule_code      VARCHAR(20)  NOT NULL,
    result         JSONB        NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    CONSTRAINT uk_rule_execution UNIQUE(transaction_id, rule_code)
);

CREATE INDEX idx_rule_execution_lookup ON rule_execution(transaction_id, rule_code);
