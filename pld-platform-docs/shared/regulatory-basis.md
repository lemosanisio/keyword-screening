# Base regulatória e tradução em controles

Este documento é uma trilha inicial de requisitos para Produto e Engenharia. A interpretação e a vigência devem ser confirmadas por Compliance/Jurídico antes da entrada em produção.

## Fontes oficiais consultadas

- [Circular BCB nº 3.978/2020 — texto consolidado disponibilizado pelo Banco Central](https://normativos.bcb.gov.br/Lists/Normativos/Attachments/50905/Circ_3978_v3_P.pdf)
- [COAF — Perguntas frequentes sobre comunicação de operações](https://www.gov.br/coaf/pt-br/acesso-a-informacao/perguntas-frequentes-3)
- [CNJ — Glossário da API Pública do DataJud](https://datajud-wiki.cnj.jus.br/api-publica/glossario/)
- [CNJ — Termo de uso da API Pública do DataJud](https://datajud-wiki.cnj.jus.br/api-publica/termo-uso/)
- [Google Maps JavaScript API — Street View](https://developers.google.com/maps/documentation/javascript/streetview)
- [Google Maps Platform — políticas de Street View](https://developers.google.com/maps/documentation/streetview/policies)
- [Google Maps Platform — termos](https://cloud.google.com/maps-platform/terms)

## Controles derivados para o produto

| Tema | Tradução para requisito de sistema |
|---|---|
| Abordagem baseada em risco | políticas versionadas, perfil de risco atual e histórico, critérios explicáveis e revisão por eventos |
| Procedimentos de conhecer cliente e relações | modelo PF/PJ, beneficiários/representantes, fontes e revalidação de fatos |
| Monitoramento e análise | ciclos, sinais, casos, avaliação transacional e documentação da conclusão |
| Decisão de comunicar | objeto de decisão separado, ator autorizado, instante e justificativa |
| Prazo de comunicação | relógio regulatório calculado a partir da decisão e monitorado; a Circular 3.978 estabelece comunicação até o dia útil seguinte à decisão, no escopo nela previsto |
| Sigilo / não tipping-off | acesso restrito, notificações seguras e nenhuma exposição da comunicação em canais do cliente |
| Conservação de registros | política de retenção de 10 anos para registros abrangidos pela Circular, validada por classe de dado; não depender de logs efêmeros |
| Evidência da análise | dossiê versionado com fatos, fontes, regras, atores, decisões e integridade |
| Canal COAF | porta de submissão independente do meio; o FAQ oficial contempla comunicação manual, em lote e por webservice conforme habilitação/formato |

O produto deve armazenar a regra/prazo configurado e sua fonte normativa. Se a norma mudar, novos ciclos usam a versão nova sem reescrever decisões históricas.

## Processos judiciais e DataJud

A API pública do DataJud é adequada para enriquecer um processo conhecido com metadados como número, classe, assuntos e movimentos. O contrato público não deve ser presumido como mecanismo de descoberta confiável de processos por CPF/CNPJ ou nome: os campos públicos descritos não oferecem identidade completa das partes.

Requisitos:

- guardar número e tribunal, termos consultados, data, resposta e versão do adaptador;
- separar descoberta do processo de enriquecimento do processo;
- resolver identidade antes de atribuir um processo a uma `Party`;
- distinguir polo/participação, assunto, fase, decisão e eventual condenação;
- não reduzir “processado” ou “condenado” a uma palavra-chave;
- revisar termos de uso, volume e finalidade com Jurídico/Segurança antes da integração comercial.

## Mandados, sanções, mídia e fontes públicas

- Cada fonte exige avaliação de licenciamento, finalidade, dados pessoais, automação permitida e retenção.
- Uma página pública não implica autorização para scraping em escala.
- Se não houver API/contrato adequado, desenhar importação controlada, fornecedor homologado ou consulta humana assistida.
- Guardar atribuição e data. Conteúdo mutável deve ter snapshot permitido ou hash/referência capaz de sustentar a conclusão.
- Matching por nome ou apelido sempre inclui desambiguação e confiança.

## Street View

A UI pode incorporar a experiência oficial de Street View no browser. Deve:

- usar a API e a conta/chave aprovadas;
- preservar atribuições e restrições de exibição;
- não baixar, recortar, cachear, re-hospedar ou usar a imagem como dataset sem autorização específica;
- limitar persistência a referências/metadados permitidos, como `panoId`, e à observação autoral do analista;
- sinalizar que a imagem pode ser antiga e não constitui prova isolada;
- submeter o uso de endereço residencial de PF a avaliação de privacidade e necessidade.

## Checklist de validação antes da produção

- [ ] Compliance aprovou taxonomia, decisão automática e motivos de deriva.
- [ ] Jurídico confirmou versão e aplicabilidade das normas por entidade/segmento.
- [ ] Encarregado/DPO avaliou fontes, endereço, mídia, processos e retenção.
- [ ] Segurança aprovou RBAC, segredo COAF, criptografia e auditoria de acesso.
- [ ] Contratos/licenças permitem consulta, armazenamento e exibição pretendidos.
- [ ] Prazo regulatório tem relógio, alertas, exceções de calendário e runbook.
- [ ] Retenção e legal hold foram parametrizados por classe de registro.
- [ ] Narrativa e payload de comunicação foram validados pelo responsável PLD.
- [ ] Fluxos de retificação, indisponibilidade e contingência foram exercitados.

