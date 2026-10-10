# Registro do uso de IA

Evidências do desenvolvimento assistido por IA exigido pelo item 5 do enunciado. Tudo aqui vem da
sessão real; nada foi reescrito como se fosse transcrição.

| Arquivo | Conteúdo | Origem |
|---|---|---|
| [`sessao-claude-code.md`](sessao-claude-code.md) | Sessão em ordem cronológica: todos os pedidos do usuário na íntegra (o prompt inicial, marcado como tal, os seguintes e o enviado durante uma execução), as respostas do clarify, as mensagens do assistente e a lista de ferramentas chamadas | Gerado do log JSONL do Claude Code por [`exportar_sessao.py`](exportar_sessao.py) |
| [`sessao-claude-code-2.md`](sessao-claude-code-2.md) | Segunda sessão, no mesmo formato: remoção do Lombok, análise da entrega contra o PDF, teste diferencial, colunas localizadas pelo nome e preparação da entrega (passos 16 e 17 abaixo) | Gerado do log JSONL da segunda sessão pelo mesmo script |
| [`exportar_sessao.py`](exportar_sessao.py) | Script de exportação, com as regras de omissão e sanitização no cabeçalho | Escrito na sessão |
| [`teste_diferencial.py`](teste_diferencial.py) | Teste diferencial: gera CSVs aleatórios, sobe o jar real com cada um e compara a resposta da API com um oráculo independente (ver seção abaixo) | Escrito na sessão |
| [`../revisao-tecnica.md`](../revisao-tecnica.md) | Estado atual da entrega e histórico das cinco passadas de revisão | Produzido na sessão |
| [`../../specs/001-intervalo-premios-produtores/`](../../specs/001-intervalo-premios-produtores) | Artefatos do Spec Kit (spec com clarify, plan, research, data-model, tasks) | Gerados pelos comandos do Spec Kit na sessão e atualizados nas etapas seguintes |

**O que a exportação omite:** o raciocínio interno do modelo, as saídas das ferramentas (logs de
build, conteúdo de arquivos), as instruções das skills carregadas e os lembretes do sistema. Também
foram removidos o e-mail, o caminho do perfil e o login do usuário. A exportação cobre a sessão até o
momento em que foi gerada; a resposta do assistente que a gerou não está nela. Para atualizá-la a
partir do log original:

```bash
python docs/ai/exportar_sessao.py ~/.claude/projects/<projeto>/<sessao>.jsonl docs/ai
python docs/ai/exportar_sessao.py ~/.claude/projects/<projeto>/<sessao-2>.jsonl docs/ai sessao-claude-code-2.md
```

## Ferramentas

| Ferramenta | Uso |
|---|---|
| Claude Code (CLI, Windows), modelo Claude Opus 5.5 | Agente que conduziu análise, especificação, código, testes, Docker, CI e documentação |
| GitHub Spec Kit 1.1.2 (integração `claude`, scripts PowerShell) | Fluxo de especificação, executado pelas skills `speckit-*` instaladas em `.claude/skills/` |
| Skill `skill-claude-backend-spring` | Padrões obrigatórios de código Java/Spring, lida integralmente (SKILL.md e os 9 anexos) antes de implementar |
| JDK 21.0.9, Maven 3.9.15 (wrapper), Docker 29.1.3 | Build, testes e validação do container |
| `jshell` (JDK 21) | Experimento com o Commons CSV antes da correção das aspas malformadas |
| Python 3 | Exportação da sessão e teste diferencial com oráculo independente do cálculo |

## Skills e comandos executados, em ordem

1. Leitura das fontes: PDF, CSV, `SKILL.md` e os 9 arquivos complementares da skill.
2. `speckit-constitution` — constitution 1.0.0, com 7 princípios e a hierarquia PDF > skill > constitution > spec.
3. `speckit-specify` — `spec.md` e checklist de qualidade (16/16).
4. `speckit-clarify` — 3 perguntas ao usuário, respondidas e incorporadas à spec (ver abaixo).
5. `speckit-plan` — `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md` (os
   dois últimos foram depois incorporados ao README, na organização da documentação).
6. `speckit-tasks` — 36 tarefas por história de usuário.
7. `speckit-analyze` — achado **CRITICAL**: a constitution dizia que linha inválida interrompe a subida, ao contrário da decisão do clarify.
8. `speckit-constitution` — emenda PATCH 1.0.1 para alinhar a constitution ao clarify.
9. `speckit-implement` — implementação, testes, Docker, CI, documentação e revisão final (T001–T036).
10. **Auditoria** pedida pelo usuário, sem alterar arquivos: código relido, build, Docker e comandos
    documentados reexecutados, CSVs de borda testados fora do repositório. Resultado: 0 bloqueantes,
    3 relevantes e 9 melhorias opcionais.
11. **Correção e estabilização** pedidas pelo usuário (R1, O1, O3, O5, O7, O9, R2): tarefas
    T037–T045 acrescentadas ao `tasks.md` e executadas; os artefatos da spec foram atualizados.
12. **Validação técnica final** pedida pelo usuário, sem operações de git, que ficam com ele: build,
    oráculo independente em Python comparado com a API, medição de desempenho com CSV sintético de
    20.000 filmes, Docker, revisão do README e remoção de dois membros sem uso (T046).
13. **Revisão de complexidade estrutural** pedida pelo usuário, sem alterar arquivos: cada classe
    avaliada contra KISS/YAGNI e contra as regras obrigatórias da skill. Das propostas apresentadas,
    o usuário aprovou só a R-1 (remover `FilmeConstants`), aplicada e validada (T047).
14. **Organização da documentação** pedida pelo usuário: mapa dos documentos no README, revisão
    técnica com estado atual e histórico em ordem, este índice sem duplicidade e artefatos da spec
    atualizados (T048).
15. **Consolidação de pastas** pedida pelo usuário: contrato, quickstart e checklist incorporados
    ao README e à spec; prompt inicial dentro da sessão exportada; cópia local da skill movida da
    raiz para `.local/` (ignorada pelo git) (T049).
16. **Remoção do Lombok** pedida pelo usuário. A skill exige Lombok; o conflito foi perguntado ao
    usuário, que decidiu manter sem ele. Desvio registrado em research R9 (T050).
17. **Robustez a outros conjuntos de dados**, pedida pelo usuário a partir do aviso do enunciado:
    teste diferencial com arquivos aleatórios e variações de formato contra o jar real. Achado:
    cabeçalho com outra caixa e `;` no fim da linha impediam a subida. A leitura passou a localizar
    as colunas pelo nome, aceitando outra ordem e colunas extras (T051, T052).

## Teste diferencial com dados aleatórios

O enunciado avisa que a avaliação usa outros conjuntos de dados. Os testes de integração cobrem
cenários escolhidos à mão; o [`teste_diferencial.py`](teste_diferencial.py) complementa com
cenários que ninguém escolheu. Para cada semente ele sorteia um arquivo de filmes, sobe o jar real
com esse arquivo e compara a resposta HTTP com o resultado esperado.

- **Oráculo independente:** o esperado sai dos dados que o próprio gerador sorteou, sem ler o CSV.
  Um erro de leitura (separador de produtores, aspas, BOM, ordem das colunas) aparece como
  diferença em vez de ser repetido pelo oráculo.
- **Cenários sorteados:** empates forçados no mínimo e no máximo, mesmo produtor no mínimo e no
  máximo, intervalo 0, vários produtores por filme com separadores mistos e nomes parecidos com
  `and` (`Anderson`, `Sandy Andrews`), aspas, `winner` em outra caixa, colunas em ordem aleatória,
  cabeçalho em outra caixa, colunas extras, BOM e CRLF. A lista completa está no cabeçalho do script.
- **Prova de que ele detecta erro:** com o oráculo sabotado de propósito, todas as sementes falharam.
- **Por que fica fora do Maven:** o enunciado pede somente testes de integração com dados
  conhecidos, e um segundo cálculo em Java dentro do projeto seria código duplicado.

Resultado na última execução: 60 arquivos, 0 falhas. Para executar, na raiz, depois de `./mvnw package`:

```bash
python docs/ai/teste_diferencial.py 60
```

O que ele já encontrou: cabeçalho com outra caixa e `;` sobrando no fim da linha impediam a subida
da aplicação. A leitura passou a localizar cada coluna pelo nome, aceitando outra ordem e colunas
extras, com o teste de integração `ArquivoColunasEmOutraOrdem`.

## Decisões de negócio tomadas pelo usuário

| Pergunta | Resposta do usuário (resumo; texto integral na sessão) |
|---|---|
| Duas vitórias do mesmo produtor no mesmo ano contam como intervalo 0? | Sim, intervalo 0. |
| Linha inválida no CSV deve impedir a subida? | Não. Descartar a linha com WARN (número e motivo) e continuar; falhar só em erro estrutural (arquivo inexistente, cabeçalho incompatível); sem tabela de rejeitados. |
| Nomes que diferem só em caixa são o mesmo produtor? | Não. Comparação exata, removendo só os espaços das extremidades, com teste dedicado. |

Diretivas do usuário na etapa de correção pós-auditoria (texto integral na sessão): aspas malformadas não podem
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
| 15 | O exportador classificava como "resposta ao clarify" a saída de um `grep` que continha a frase da ferramenta, e não captava a mensagem que o usuário enviou durante a execução (gravada no log como anexo `queued_command`) | Conferência do arquivo exportado contra o log | Resposta só é aceita se vier de uma chamada `AskUserQuestion` (pelo id); mensagens enviadas durante a execução passaram a ser exportadas com rótulo próprio |
| 16 | O último pedido do usuário chegou cortado no item 5 e a versão completa veio durante a execução | Leitura da mensagem | Itens 1 a 5 iniciados pela versão cortada; os demais executados pela versão completa |

## Resultados das validações

Detalhes em [`../revisao-tecnica.md`](../revisao-tecnica.md) (passadas 3 e 4). Em resumo:

- 28 testes de integração verdes (27 também validados no Linux, simulando a CI, antes do teste de colunas em outra ordem).
- Resposta da API idêntica à de um oráculo independente, inclusive num CSV sintético de 20.000
  filmes; consulta de 25 a 90 ms nesse volume.
- Teste diferencial: 60 arquivos aleatórios, 0 falhas.
- Docker build com imagens fixadas, run, endpoint, troca de CSV por volume e stop verificados, sem Actuator.
- A pipeline do GitHub Actions **não** foi executada no GitHub, porque o repositório não foi publicado.
