CREATE TABLE transaction_evaluation_execution (
    evaluation_id VARCHAR(30) NOT NULL REFERENCES transaction_evaluation(evaluation_id),
    decision_execution_id UUID NOT NULL REFERENCES decision_execution(id),
    rule_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (evaluation_id, decision_execution_id),
    CONSTRAINT ux_transaction_evaluation_execution_unique
        UNIQUE (decision_execution_id)
);

INSERT INTO transaction_evaluation_execution (evaluation_id, decision_execution_id, rule_code)
SELECT e.evaluation_id, e.decision_execution_id,
       COALESCE(e.rules_executed -> 0 ->> 'ruleCode', 'UNKNOWN')
FROM transaction_evaluation e;
