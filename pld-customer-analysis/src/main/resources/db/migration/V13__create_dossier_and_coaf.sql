-- Dossiê interno: artefato gerado a partir de evidências + decisões do caso.
CREATE TABLE dossier (
    id              VARCHAR(30)  PRIMARY KEY,
    case_id         VARCHAR(30)  NOT NULL,
    party_id        VARCHAR(30)  NOT NULL,
    version         INT          NOT NULL DEFAULT 1,
    status          VARCHAR(20)  NOT NULL DEFAULT 'GENERATING',
    as_of           TIMESTAMP    NOT NULL,
    generated_at    TIMESTAMP,
    manifest_hash   VARCHAR(64),
    policy_version  VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE dossier_section (
    id              VARCHAR(30)  PRIMARY KEY,
    dossier_id      VARCHAR(30)  NOT NULL REFERENCES dossier(id),
    section_code    VARCHAR(50)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    object_type     VARCHAR(50),
    object_id       VARCHAR(30),
    object_version  VARCHAR(10),
    included        BOOLEAN      NOT NULL DEFAULT true,
    gap_reason      VARCHAR(100),
    content         JSONB        NOT NULL DEFAULT '{}',
    display_order   INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_dossier_case ON dossier (case_id);
CREATE INDEX idx_dossier_section_dossier ON dossier_section (dossier_id);

-- Comunicação COAF: workflow de submissão regulatória.
CREATE TABLE coaf_communication (
    id                  VARCHAR(30)  PRIMARY KEY,
    case_id             VARCHAR(30)  NOT NULL,
    party_id            VARCHAR(30)  NOT NULL,
    dossier_id          VARCHAR(30)  REFERENCES dossier(id),
    status              VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    version             INT          NOT NULL DEFAULT 1,
    operation_type      VARCHAR(50),
    operation_value     VARCHAR(50),
    operation_date      DATE,
    narrative           TEXT,
    legal_framework     VARCHAR(200),
    protocol_number     VARCHAR(50),
    rejection_reason    TEXT,
    deadline_days       INT,
    deadline_start      TIMESTAMP,
    previous_id         VARCHAR(30)  REFERENCES coaf_communication(id),
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    submitted_at        TIMESTAMP,
    acknowledged_at     TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE coaf_communication_event (
    id                  VARCHAR(30)  PRIMARY KEY,
    communication_id    VARCHAR(30)  NOT NULL REFERENCES coaf_communication(id),
    event_type          VARCHAR(50)  NOT NULL,
    actor_id            VARCHAR(100) NOT NULL,
    actor_role          VARCHAR(30)  NOT NULL,
    detail              TEXT,
    occurred_at         TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_coaf_comm_case ON coaf_communication (case_id);
CREATE INDEX idx_coaf_event_comm ON coaf_communication_event (communication_id);
