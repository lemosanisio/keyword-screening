-- Decisões históricas de analistas para RAG/few-shot learning
-- Requirements: 2.1, 8.1, 8.2
CREATE TABLE historical_decision (
    id               BIGSERIAL    PRIMARY KEY,
    keyword          VARCHAR(255) NOT NULL,
    description      TEXT         NOT NULL,
    analyst_decision VARCHAR(50)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_hist_decision_keyword ON historical_decision(keyword);
CREATE INDEX idx_hist_decision_keyword_date ON historical_decision(keyword, created_at DESC);
