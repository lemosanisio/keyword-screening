CREATE TABLE restricted_term (
    id         BIGSERIAL    PRIMARY KEY,
    term       VARCHAR(255) NOT NULL,
    category   VARCHAR(50)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_restricted_term_active ON restricted_term(active);
