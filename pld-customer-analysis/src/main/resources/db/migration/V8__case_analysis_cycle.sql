ALTER TABLE pld_case ADD COLUMN analysis_cycle_id VARCHAR(32);

CREATE INDEX idx_pld_case_analysis_cycle ON pld_case (analysis_cycle_id);
