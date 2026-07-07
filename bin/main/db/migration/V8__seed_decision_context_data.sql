-- Seed data do MVP: Rule Catalog + Fact Registry
-- Requirements: 1.6, 2.5, 15.5

-- =============================================================================
-- 1. Entity Definitions
-- =============================================================================
INSERT INTO entity_definition (id, name, display_name, source_system, fact_names)
VALUES
    (gen_random_uuid(), 'Risk', 'Risco', 'PLD', '["customerRisk"]'::JSONB),
    (gen_random_uuid(), 'Screening', 'Screening', 'Screening', '["keywordMatched"]'::JSONB)
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- 2. Fact Definitions
-- =============================================================================
INSERT INTO fact_definition (id, name, display_name, entity, type, context, source, supported_operators, enabled)
VALUES
    (gen_random_uuid(), 'keywordMatched', 'Keyword Matched', 'Screening', 'BOOLEAN', 'SCREENING', 'Screening',
     '["EQUALS","NOT_EQUALS"]'::JSONB, TRUE),
    (gen_random_uuid(), 'customerRisk', 'Customer Risk', 'Risk', 'ENUM', 'SCREENING', 'PLD',
     '["EQUALS","NOT_EQUALS","GREATER_THAN_OR_EQUAL"]'::JSONB, TRUE)
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- 3. Rule Definition
-- =============================================================================
INSERT INTO rule_definition (id, code, name, description, context, category, supported_facts, supported_actions, status)
VALUES
    (gen_random_uuid(), 'KEYWORD_SCREENING', 'Keyword Screening',
     'Regra MF09 — detecção de termos restritos em descrições de transações PIX',
     'SCREENING', 'KEYWORD_SCREENING',
     '["keywordMatched","customerRisk"]'::JSONB,
     '["GENERATE_ALERT","IGNORE"]'::JSONB,
     'ACTIVE')
ON CONFLICT (code) DO NOTHING;
