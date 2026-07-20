-- Seed: termos restritos iniciais cadastrados pela área de Compliance
-- Todos os termos já estão normalizados (minúsculas, sem acentos, sem caracteres especiais, sem espaços duplos)

INSERT INTO restricted_term (term, category, active, created_at, updated_at) VALUES
    ('terrorismo',              'TERRORISM',      TRUE, NOW(), NOW()),
    ('financiamento ao terror', 'TERRORISM',      TRUE, NOW(), NOW()),
    ('lavagem de dinheiro',     'AML',            TRUE, NOW(), NOW()),
    ('ocultar origem',          'AML',            TRUE, NOW(), NOW()),
    ('fraude',                  'FRAUD',          TRUE, NOW(), NOW()),
    ('estelionato',             'FRAUD',          TRUE, NOW(), NOW()),
    ('crime financeiro',        'FINANCIAL_CRIME', TRUE, NOW(), NOW()),
    ('desvio de verbas',        'FINANCIAL_CRIME', TRUE, NOW(), NOW()),
    ('sancao',                  'SANCTIONS',      TRUE, NOW(), NOW()),
    ('embargo',                 'SANCTIONS',      TRUE, NOW(), NOW());
