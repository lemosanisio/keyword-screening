-- Tabelas do Decision Context (Decision Engine)
-- Requirements: 1.1, 2.1, 8.1, 9.1, 9.2, 11.1

-- =============================================================================
-- 1. entity_definition — Catálogo de entidades do domínio
-- =============================================================================
CREATE TABLE IF NOT EXISTS entity_definition (
    id            UUID                     PRIMARY KEY,
    name          VARCHAR(100)             NOT NULL,
    display_name  VARCHAR(255)             NOT NULL,
    source_system VARCHAR(100)             NOT NULL,
    fact_names    JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_entity_definition_name UNIQUE (name)
);

-- =============================================================================
-- 2. fact_definition — Catálogo de fatos disponíveis para regras
-- =============================================================================
CREATE TABLE IF NOT EXISTS fact_definition (
    id                  UUID                     PRIMARY KEY,
    name                VARCHAR(100)             NOT NULL,
    display_name        VARCHAR(255)             NOT NULL,
    entity              VARCHAR(100)             NOT NULL,
    type                VARCHAR(50)              NOT NULL,
    context             VARCHAR(100)             NOT NULL,
    source              VARCHAR(100)             NOT NULL,
    supported_operators JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    enabled             BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_fact_definition_name UNIQUE (name)
);

-- =============================================================================
-- 3. rule_definition — Catálogo de regras de decisão
-- =============================================================================
CREATE TABLE IF NOT EXISTS rule_definition (
    id               UUID                     PRIMARY KEY,
    code             VARCHAR(50)              NOT NULL,
    name             VARCHAR(255)             NOT NULL,
    description      TEXT,
    context          VARCHAR(100)             NOT NULL,
    category         VARCHAR(100)             NOT NULL,
    supported_facts  JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    supported_actions JSONB                   NOT NULL DEFAULT '[]'::JSONB,
    status           VARCHAR(50)              NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rule_definition_code UNIQUE (code)
);

-- =============================================================================
-- 4. rule_configuration — Configuração ativa de uma regra (expressões + ações)
-- =============================================================================
CREATE TABLE IF NOT EXISTS rule_configuration (
    id              UUID                     PRIMARY KEY,
    rule_id         UUID                     NOT NULL,
    expressions     JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    actions         JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    active          BOOLEAN                  NOT NULL DEFAULT FALSE,
    draft           BOOLEAN                  NOT NULL DEFAULT TRUE,
    current_version INTEGER                  NOT NULL DEFAULT 1,
    created_by      VARCHAR(100)             NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_rule_configuration_rule FOREIGN KEY (rule_id) REFERENCES rule_definition (id)
);

-- Partial unique index: apenas uma configuração ativa por regra
CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_configuration_active
    ON rule_configuration (rule_id)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_rule_configuration_rule_id ON rule_configuration (rule_id);

-- =============================================================================
-- 5. configuration_version — Histórico de versões de configuração
-- =============================================================================
CREATE TABLE IF NOT EXISTS configuration_version (
    id               UUID                     PRIMARY KEY,
    configuration_id UUID                     NOT NULL,
    version          INTEGER                  NOT NULL,
    expressions      JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    actions          JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    active           BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_by       VARCHAR(100)             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_configuration_version_config FOREIGN KEY (configuration_id) REFERENCES rule_configuration (id),
    CONSTRAINT uk_configuration_version UNIQUE (configuration_id, version)
);

CREATE INDEX IF NOT EXISTS idx_configuration_version_config_id ON configuration_version (configuration_id);

-- =============================================================================
-- 6. decision_execution — Registro de execuções de decisão (auditoria)
-- =============================================================================
CREATE TABLE IF NOT EXISTS decision_execution (
    id                    UUID                     PRIMARY KEY,
    transaction_id        VARCHAR(100)             NOT NULL,
    rule_id               UUID                     NOT NULL,
    configuration_version INTEGER                  NOT NULL,
    facts                 JSONB                    NOT NULL DEFAULT '{}'::JSONB,
    decision              VARCHAR(50)              NOT NULL,
    actions               JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    matched_expressions   JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    failed_expressions    JSONB                    NOT NULL DEFAULT '[]'::JSONB,
    explanation           JSONB                    NOT NULL DEFAULT '{}'::JSONB,
    execution_time_ms     BIGINT                   NOT NULL,
    trace_id              VARCHAR(100),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_decision_execution_idempotency UNIQUE (transaction_id, rule_id)
);

CREATE INDEX IF NOT EXISTS idx_decision_execution_transaction_id ON decision_execution (transaction_id);
CREATE INDEX IF NOT EXISTS idx_decision_execution_rule_id ON decision_execution (rule_id);
CREATE INDEX IF NOT EXISTS idx_decision_execution_trace_id ON decision_execution (trace_id);
CREATE INDEX IF NOT EXISTS idx_decision_execution_decision ON decision_execution (decision);

-- =============================================================================
-- 7. dry_run_log — Log de execuções dry-run (simulação)
-- =============================================================================
CREATE TABLE IF NOT EXISTS dry_run_log (
    id               UUID                     PRIMARY KEY,
    configuration_id UUID                     NOT NULL,
    version          INTEGER                  NOT NULL,
    facts            JSONB                    NOT NULL DEFAULT '{}'::JSONB,
    result           JSONB                    NOT NULL DEFAULT '{}'::JSONB,
    executed_by      VARCHAR(100)             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_dry_run_log_config FOREIGN KEY (configuration_id) REFERENCES rule_configuration (id)
);

CREATE INDEX IF NOT EXISTS idx_dry_run_log_config_id ON dry_run_log (configuration_id);
