ALTER TABLE transaction_evaluation
    ADD COLUMN failure_stage VARCHAR(30),
    ADD COLUMN failure_code VARCHAR(128),
    ADD CONSTRAINT ck_transaction_evaluation_failure_stage
        CHECK (failure_stage IN ('FACT_RESOLUTION', 'RULE_EVALUATION', 'DECISION')),
    ADD CONSTRAINT ck_transaction_evaluation_failed_fields
        CHECK (
            (execution_status = 'FAILED' AND failure_stage IS NOT NULL AND failure_code IS NOT NULL
                AND evaluation_outcome IS NULL AND review_required IS NULL AND recommended_route IS NULL)
            OR (execution_status <> 'FAILED' AND failure_stage IS NULL AND failure_code IS NULL)
        );
