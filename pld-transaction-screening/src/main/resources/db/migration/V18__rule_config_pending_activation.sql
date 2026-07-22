-- Maker-checker para ativação de regras: pending_activation + approved_by
ALTER TABLE rule_configuration ADD COLUMN IF NOT EXISTS pending_activation BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE rule_configuration ADD COLUMN IF NOT EXISTS activation_requested_by VARCHAR(100);
ALTER TABLE rule_configuration ADD COLUMN IF NOT EXISTS activation_approved_by VARCHAR(100);
