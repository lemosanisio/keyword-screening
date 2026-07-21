ALTER TABLE suspicion_decision
    ADD COLUMN approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN approved_by_actor_id VARCHAR(128),
    ADD COLUMN approved_by_actor_role VARCHAR(64),
    ADD COLUMN approved_at TIMESTAMPTZ;

ALTER TABLE account_decision
    ADD COLUMN approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN approved_by_actor_id VARCHAR(128),
    ADD COLUMN approved_by_actor_role VARCHAR(64),
    ADD COLUMN approved_at TIMESTAMPTZ;
