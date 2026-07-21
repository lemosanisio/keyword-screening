ALTER TABLE decision_execution
    ADD COLUMN evaluation_id VARCHAR(30),
    ADD COLUMN party_id VARCHAR(30),
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN causation_id VARCHAR(128);

CREATE UNIQUE INDEX ux_decision_execution_evaluation_id
    ON decision_execution (evaluation_id)
    WHERE evaluation_id IS NOT NULL;
