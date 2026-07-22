ALTER TABLE transaction_evaluation_projection
    ADD COLUMN failure_stage VARCHAR(30),
    ADD COLUMN failure_code VARCHAR(128);
