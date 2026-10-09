# Registro do uso de IA

Evidências do desenvolvimento assistido por IA exigido pelo item 5 do enunciado. Tudo aqui vem da
sessão real; nada foi reescrito como se fosse transcrição.

| Arquivo | Conteúdo | Origem |
|---|---|---|
| [`prompt-inicial.md`](prompt-inicial.md) | Prompt que iniciou o trabalho, na íntegra | Extraído do log da sessão |
| [`sessao-claude-code.md`](sessao-claude-code.md) — seções "Usuário" seguintes | Pedidos posteriores do usuário (auditoria e correção), na íntegra | Extraídos do log da sessão |
| [`sessao-claude-code.md`](sessao-claude-code.md) | Exportação sanitizada da sessão: mensagens do usuário, respostas do clarify, mensagens do assistente e lista de ferramentas chamadas | Gerado do log JSONL do Claude Code por [`exportar_sessao.py`](exportar_sessao.py) |
| [`../revisao-tecnica.md`](../revisao-tecnica.md) | Revisão final: achados por severidade, conformidade com a skill, validações | Produzido na sessão |
| [`../../specs/001-intervalo-premios-produtores/`](../../specs/001-intervalo-premios-produtores) | Artefatos do Spec Kit (spec, clarify, plan, research, data-model, contrato, tasks) | Gerados pelos comandos do Spec Kit na sessão |

**O que a exportação omite:** o raciocínio interno do modelo, as saídas das ferramentas (logs de
build, conteúdo de arquivos), as instruções das skills carregadas e os lembretes do sistema. Também
foram removidos o e-mail e o caminho do perfil do usuário. O script está versionado para quem
quiser conferir o critério ou regenerar a partir do log original. A exportação cobre a sessão até
o momento em que foi gerada, pouco antes do commit final; o relatório final da sessão não está nela.

## Ferramentas

| Ferramenta | Uso |
|---|---|
| Claude Code (CLI, Windows), modelo Claude Opus 5.5 | Agente que conduziu análise, especificação, código, testes, Docker, CI e documentação |
| GitHub Spec Kit 1.1.2 (integração `claude`, scripts PowerShell) | Fluxo de especificação, executado pelas skills `speckit-*` instaladas em `.claude/skills/` |
| Skill `skill-claude-backend-spring` | Padrões obrigatórios de código Java/Spring, lida integralmente (SKILL.md e os 9 anexos) antes de implementar |
| JDK 21.0.9, Maven 3.9.15 (wrapper), Docker 29.1.3 | Build, testes e validação do container |

## Skills e comandos executados, em ordem

1. Leitura das fontes: PDF, CSV, `SKILL.md` e os 9 arquivos complementares da skill.
2. `speckit-constitution` — constitution 1.0.0, com 7 princípios e a hierarquia PDF > skill > constitution > spec.
3. `speckit-specify` — `spec.md` e checklist de qualidade (16/16).
4. `speckit-clarify` — 3 perguntas ao usuário, respondidas e incorporadas à spec (ver abaixo).
5. `speckit-plan` — `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`.
6. `speckit-tasks` — 36 tarefas por história de usuário.
7. `speckit-analyze` — achado **CRITICAL**: a constitution dizia que linha inválida interrompe a subida, ao contrário da decisão do clarify.
8. `speckit-constitution` — emenda PATCH 1.0.1 para alinhar a constitution ao clarify.
9. `speckit-implement` — implementação, testes, Docker, CI, documentação e revisão final (T001–T036).
10. **Auditoria** pedida pelo usuário, sem alterar arquivos: código relido, build, Docker e comandos
    documentados reexecutados, CSVs de borda testados fora do repositório. Resultado: 0 bloqueantes,
    3 relevantes e 9 melhorias opcionais.
11. **Correção e estabilização** pedidas pelo usuário (R1, O1, O3, O5, O7, O9, R2): tarefas
    T037–T045 acrescentadas ao `tasks.md` e executadas; os artefatos da spec foram atualizados.

## Decisões de negócio tomadas pelo usuário (clarify)

| Pergunta | Resposta do usuário (resumo; texto integral na sessão) |
|---|---|
| Duas vitórias do mesmo produtor no mesmo ano contam como intervalo 0? | Sim, intervalo 0. |
| Linha inválida no CSV deve impedir a subida? | Não. Descartar a linha com WARN (número e motivo) e continuar; falhar só em erro estrutural (arquivo inexistente, cabeçalho incompatível); sem tabela de rejeitados. |
| Nomes que diferem só em caixa são o mesmo produtor? | Não. Comparação exata, removendo só os espaços das extremidades, com teste dedicado. |

Diretivas do usuário na etapa de correção (texto integral na sessão): aspas malformadas não podem
interromper a importação inteira nem ser aceitas corrompidas; `and` como palavra inteira em qualquer
caixa, sem mudar a comparação exata dos nomes; duplicata exata não pode gerar prêmio artificial,
preservando o intervalo 0 de filmes distintos; remover só configuração comprovadamente
desnecessária; avaliar Actuator e fixação das imagens.

## Decisões técnicas e alternativas

Registradas com alternativas e trade-offs em
[`research.md`](../../specs/001-intervalo-premios-produtores/research.md). As principais:

- JPA para gravar e uma `@Query` nativa ordenada para ler, com cálculo linear em Java. Avaliados e
  descartados: `JdbcClient` para tudo, QueryDSL, `LAG` no SQL e cache.
- `schema.sql` em vez de Liquibase; sem springdoc nem MapStruct (desvios da skill justificados em R9).
- Importação por `SmartInitializingSingleton` em vez de `ApplicationRunner`, para não servir o banco
  vazio durante a importação.
- Somente testes de integração com HTTP real; resultados esperados calculados à mão e comentados em
  cada cenário.
- Leitura do CSV linha a linha, depois de investigar o Commons CSV em `jshell`: `trailingData` e
  `lenientEof` foram rejeitados porque alteram ou perdem dados em silêncio (research R5).
- Actuator removido por falta de consumidor real (research R9); imagens Docker fixadas por versão e
  digest (research R11).

## Problemas encontrados e correções

| # | Problema | Como foi detectado | Correção |
|---|---|---|---|
| 1 | Constitution contradizia a decisão do clarify | `speckit-analyze` (CRITICAL) | Emenda 1.0.1 |
| 2 | `ApplicationRunner` deixaria a API responder com o banco vazio durante a importação | Análise do ciclo de vida do Spring Boot | `SmartInitializingSingleton`; log confirma a importação antes de "Tomcat started" |
| 3 | Teste de troca de arquivo lia o CSV padrão | Falha de 4 testes em `ImportacaoFilmeJobTest` | `SpringApplicationBuilder.properties()` vira propriedade padrão de menor precedência; o teste passou a usar argumentos de linha de comando |
| 4 | `repackage` falhou (jar em uso) | Build | Duas instâncias da aplicação que o próprio agente subiu para teste manual ainda rodavam; foram encerradas |
| 5 | Container não subia (`No qualifying bean of type String`) | Validação real do Docker | `lombok.config` passou a ser copiado no `Dockerfile` |
| 6 | `mvnw` sem bit de execução no git (Windows) | Revisão antes da CI | `git update-index --chmod=+x`; validado em container Linux com `git archive` |
| 7 | CSV com BOM (Excel/Windows) impedia a subida | Teste de integração novo criado na revisão | BOM removido antes do parser |
| 8 | Leitura duplicada de campos; literais no teste; caractere invisível no fonte | Revisão de código | Refatoração pontual |
| 9 | Heredoc do shell falhou ao criar vários arquivos de uma vez (nada foi gravado) | Erro do shell | Arquivos criados um a um com a ferramenta de escrita |
| 10 | Aspa malformada numa linha derrubava a importação inteira | Auditoria (CSV de borda fora do repositório) | Parser por linha; teste `ArquivoComAspasMalformadas` |
| 11 | `AND` em maiúsculas não separava produtores; linha duplicada criava intervalo 0 | Auditoria | Regex com `and` sem diferenciar caixa; descarte de duplicata exata; testes dedicados |
| 12 | Primeira tentativa do experimento em `jshell` falhou (`NoClassDefFoundError`, commons-io antigo no classpath montado à mão) | Erro na execução | Classpath gerado pelo próprio Maven (`dependency:build-classpath`) |
| 13 | O escape do caractere U+FEFF virou caractere invisível ao gravar o fonte (de novo) e, numa correção por `sed`, perdeu a barra e o u | Varredura de bytes do fonte | Constante escrita sem escape: `String.valueOf((char) 0xFEFF)` |
| 14 | Atalho `python` do Windows (Microsoft Store) interceptou chamadas | Erro do shell | Uso do caminho completo do interpretador |

## Resultados das validações

Ver a tabela em [`../revisao-tecnica.md`](../revisao-tecnica.md#validações-executadas). Em resumo:

- 27 testes de integração verdes no Windows e no Linux, este último simulando a CI.
- Docker build com imagens fixadas, run, endpoint, troca de CSV por volume e stop verificados, sem Actuator.
- A pipeline do GitHub Actions **não** foi executada no GitHub, porque o repositório não foi publicado.
