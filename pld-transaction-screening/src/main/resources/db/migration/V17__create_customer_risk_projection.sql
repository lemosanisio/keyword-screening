-- Projeção local do perfil de risco do cliente, atualizada por evento
-- CustomerRiskProfileUpdated.v1. Substitui chamada REST síncrona no fact resolver.
CREATE TABLE customer_risk_projection (
    party_id         VARCHAR(30)  PRIMARY KEY,
    risk_level       VARCHAR(20)  NOT NULL,
    segments         JSONB        NOT NULL DEFAULT '[]',
    transaction_facts JSONB       NOT NULL DEFAULT '{}',
    profile_version  INT          NOT NULL,
    risk_profile_id  VARCHAR(30)  NOT NULL,
    policy_version   VARCHAR(50)  NOT NULL,
    effective_from   TIMESTAMP    NOT NULL,
    valid_until      TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_risk_profile_version UNIQUE (party_id, profile_version)
);

CREATE INDEX idx_risk_projection_valid ON customer_risk_projection (valid_until);
