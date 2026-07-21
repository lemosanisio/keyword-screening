ALTER TABLE case_source
    ADD COLUMN evaluation_id VARCHAR(64),
    ADD COLUMN transaction_id VARCHAR(64),
    ADD COLUMN signal_type VARCHAR(64),
    ADD COLUMN recommended_route VARCHAR(64),
    ADD COLUMN risk_profile_version INTEGER,
    ADD COLUMN rule_matches JSONB NOT NULL DEFAULT '[]'::jsonb;
