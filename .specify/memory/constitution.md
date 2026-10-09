# API Golden Raspberry Awards — Constitution

## Core Principles

### I. O enunciado da Outsera é a autoridade máxima

Os requisitos expressos em `challenge/Avaliação - Back-end.pdf` MUST prevalecer sobre qualquer
outra fonte. A hierarquia de decisão é: **PDF > skill `skill-claude-backend-spring` > esta
constitution > especificação e plano**. Nenhum requisito do PDF pode ser relaxado para facilitar a
implementação; em especial: leitura do CSV na inicialização, persistência em banco embarcado em
memória, nível 2 de maturidade de Richardson, contrato JSON da página 2 e somente testes de
integração.

*Razão:* o avaliador valida a aderência ao enunciado com outros datasets; qualquer desvio é falha.

### II. Padrões de implementação vêm da skill `skill-claude-backend-spring`

Todo código Java/Spring MUST seguir `.claude/skills/skill-claude-backend-spring/SKILL.md` e seus
arquivos complementares (`layers-and-naming.md`, `code-style.md`, `database.md`, `testing.md`,
`solid.md`, `stack-and-build.md`, `security-and-review.md`, `cloud-native.md`,
`threads-and-virtual-threads.md`): pacotes, camadas, nomenclatura (domínio em português, esqueleto
técnico em inglês), par interface + `Impl`, injeção por construtor com campos `final`, `this.` em
todo membro, `public abstract` em método de interface, linhas em branco, SQL em caixa alta,
`@Transactional` só em `ServiceTransactional`, logs com `@Slf4j` e placeholders, tratamento global
de erros e nomenclatura de testes.

- Padrão genérico não substitui convenção existente na skill.
- Bibliotecas internas citadas pela skill (Utils compartilhados, `BusinessServiceException` da
  biblioteca da empresa) não existem neste projeto e MUST NOT ser supostas; quando necessárias, são
  criadas localmente com os mesmos nomes, conforme a própria skill orienta.
- Desvio de regra da skill MUST ser explícito e justificado na documentação (plano ou README).
- O conteúdo da skill MUST NOT ser alterado para acomodar o desafio.

*Razão:* a skill é o padrão de engenharia avaliado; desvio silencioso equivale a defeito de revisão.

### III. Simplicidade proporcional ao problema

KISS, YAGNI, DRY e SOLID (conforme `solid.md`) MUST orientar o desenho. É proibido introduzir sem
necessidade concreta: microsserviços, arquitetura hexagonal completa, CQRS, Event Sourcing,
mensageria, cache, processamento paralelo, factories/strategies, camadas ou dependências extras.
As camadas e interfaces exigidas pela skill são aplicadas somente onde existe a responsabilidade
correspondente (ex.: Facade só se houver orquestração).

*Razão:* o problema é pequeno; complexidade acidental reduz legibilidade e é penalizada na revisão.

### IV. Correção e determinismo do cálculo para qualquer dataset

- O cálculo MUST funcionar para datasets desconhecidos; resultados fixos ou regras específicas para
  produtores do arquivo original são proibidos.
- Produtores MUST ser separados individualmente (vírgula, ` and `, `, and `), removendo apenas
  os espaços das extremidades de cada nome; a comparação é exata, preservando caixa e grafia.
- Intervalos MUST ser calculados somente entre vitórias consecutivas do mesmo produtor, nunca entre
  combinações arbitrárias de anos.
- Todos os empates de mínimo e de máximo MUST ser preservados.
- A ordenação da resposta MUST ser determinística e documentada.
- Linhas inválidas no CSV MUST NOT ser descartadas silenciosamente: a linha individualmente
  inválida é descartada com log WARN (número da linha e motivo) e a importação continua; erro
  estrutural (arquivo inexistente/ilegível, cabeçalho incompatível) interrompe a inicialização com
  mensagem clara.

*Razão:* o PDF avisa que outros conjuntos de dados serão usados na avaliação.

### V. Somente testes de integração, com expectativas independentes

- Por exigência do PDF, MUST existir apenas testes de integração; testes unitários são proibidos.
- Os testes MUST exercitar a inicialização Spring, a importação do CSV, a persistência em H2, a
  regra de negócio e o endpoint HTTP real com o contrato JSON.
- Os resultados esperados MUST ser definidos a partir dos dados de entrada (cálculo manual
  documentado), nunca reaproveitando o algoritmo da aplicação.
- Cada cenário usa dataset próprio e isolado; os testes MUST ser reproduzíveis e independentes de
  ordem.
- O build completo (`./mvnw verify`) MUST passar antes de qualquer etapa ser declarada concluída.

*Razão:* é o critério do PDF e a única evidência objetiva de correção.

### VI. Reprodutibilidade e operação sem dependências externas

Java 21, Spring Boot 4.x estável, Spring MVC, Maven Wrapper, H2 embarcado em memória (nenhuma
instalação externa), Dockerfile multi-stage com runtime Java 21 e usuário não root, e pipeline
GitHub Actions que compila e testa sem deploy. O CSV padrão é o original; o caminho do arquivo MUST
ser substituível por configuração sem alterar código. Nenhuma afirmação de funcionamento (build,
testes, Docker) é feita sem execução verificada.

*Razão:* o avaliador precisa executar o projeto e os testes apenas com as instruções do README.

### VII. Rastreabilidade honesta do uso de IA

As interações com o agente MUST ser registradas em `docs/ai/` a partir de fontes reais (prompts
efetivamente enviados, exportações de sessão, resultados de comandos). Conversas, prompts ou
resultados inventados, ou resumos apresentados como transcrição, são proibidos. Credenciais e dados
sensíveis MUST NOT constar nos registros. Toda a documentação é em português brasileiro.

*Razão:* o uso de IA é obrigatório e avaliado pela Outsera; a evidência precisa ser verificável.

## Restrições Técnicas

- Nível 2 de Richardson: recurso nomeado por substantivo, verbo HTTP semântico (`GET` para
  leitura, sem alteração de estado), códigos de status adequados, sem endpoints desnecessários.
- Importação do CSV ocorre uma única vez, na inicialização — nunca por requisição.
- Consultas sem N+1 e sem acesso ao banco dentro de laço; complexidade do cálculo próxima de
  O(N + W log W) (N linhas, W pares produtor-vitória).
- Segredos fora do código; erros sem stack trace na resposta; logs sem dado sensível.
- Arquivos novos em UTF-8 sem BOM; o CSV original e a skill são preservados sem alteração.

## Fluxo de Desenvolvimento e Qualidade

- Fluxo Spec Kit: constitution → specify → clarify → plan → tasks → analyze → implement →
  validação e revisão final.
- Ambiguidade de negócio que altera o comportamento é levada ao humano na fase clarify; as
  respostas ficam registradas na especificação.
- A cada incremento relevante o código é revisto contra a skill; a revisão final produz uma
  verificação explícita de conformidade e classifica achados por severidade (Alta/Média/Baixa,
  conforme `security-and-review.md`).
- Commits incrementais e coerentes; nenhum push ou publicação sem autorização explícita.
- Artefatos de build, arquivos temporários e credenciais não são versionados.

## Governance

Esta constitution rege o projeto abaixo do PDF da Outsera e da skill `skill-claude-backend-spring`.
Emendas exigem: descrição da mudança, justificativa, incremento de versão semântico (MAJOR para
remoção/redefinição de princípio, MINOR para princípio ou seção nova, PATCH para redação) e
revisão de impacto em spec, plano e tarefas. Toda revisão de código e a análise `/speckit-analyze`
MUST verificar conformidade com os princípios; complexidade adicional MUST ser justificada no plano
(seção de rastreamento de complexidade).

**Version**: 1.0.1 | **Ratified**: 2026-10-09 | **Last Amended**: 2026-10-09
