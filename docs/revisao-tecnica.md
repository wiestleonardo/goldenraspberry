# Revisão técnica

Revisões feitas contra o PDF da Outsera, a skill
[`skill-claude-backend-spring`](../.claude/skills/skill-claude-backend-spring/SKILL.md) e a
[constitution](../.specify/memory/constitution.md), lendo o código-fonte (não só relatórios).
Formato dos achados conforme `security-and-review.md` da skill: veredito + severidade por
probabilidade × impacto.

Primeiro vem o **estado atual** da entrega; depois, o **histórico** das cinco passadas, todas em
2026-10-09.

## Estado atual

### Requisitos do PDF

| Requisito | Evidência | Resultado |
|---|---|---|
| Ler o CSV e inserir em base ao iniciar | `ImportacaoFilmeJob` → `ImportacaoFilmeFacade`; log "206 filmes gravados" antes de "Tomcat started" | ✅ |
| Maior intervalo e dois prêmios mais rápido no formato da pág. 2 | `GET /api/produtores/intervalosPremios`; teste estrito com o CSV original | ✅ |
| Richardson nível 2 | recurso por URI, `GET` sem efeito colateral, 200/404/405, JSON | ✅ |
| Somente testes de integração | 27 testes, nenhum unitário; todos sobem o contexto real | ✅ |
| Banco em memória embarcado, sem instalação | H2 `mem`, nome único por contexto | ✅ |
| README com execução e testes | `README.md`; comandos validados | ✅ |
| Repositório git | gestão do git feita pelo usuário | — |
| Registro de interações com IA | `docs/ai/` | ✅ |
| Precisão com outros datasets | 18 datasets alternativos com resultado calculado à mão; oráculo independente (passada 4) | ✅ |

### Conformidade com a skill

| Regra | Situação |
|---|---|
| Estrutura de pacotes e fluxo de injeção (Job → Facade → Service/ServiceTransactional → Repository; Controller → Service) | ✅ |
| Par interface + `Impl`; Controller com contrato na interface e `Impl` que só delega; rota em `RotaConstants` | ✅ |
| Injeção por construtor com `final`; `@Value` no parâmetro do construtor; nenhum `@Autowired` em produção | ✅ |
| `this.` em todo membro; `public abstract` em interface; linha em branco no início de bloco; ordem dos membros | ✅ |
| Constantes `private static final` no topo da única classe que as usa; regex em `RegexConstants`; índices e números nomeados (`INDICE_COLUNA_*`, `NUMERO_LINHA_CABECALHO`, `LongUtil.UM`) | ✅ |
| Comparação e cálculo pelo Util (`ObjectUtil.isEquals`, `LongUtil.igual/menos/soma`) | ✅ (Utils mínimos locais) |
| `@Transactional(rollbackFor = Exception.class)` só no `ServiceTransactional`, sem I/O de arquivo dentro | ✅ |
| JPA: `open-in-view=false`, associação `LAZY`, `saveAll` em lote; SQL em caixa alta; nomenclatura `tb_/id_/cd_/nr_/tx_/fl_/gen_` | ✅ |
| DTO `record` singular, `Retorno*DTO`, construído pelo construtor canônico (sem Lombok, ver research R9); Projection `record` | ✅ |
| `Logger` SLF4J (`LoggerFactory`) com placeholders, sem dado sensível; WARN por linha descartada, resumo em INFO | ✅ |
| `@RestControllerAdvice` estendendo `ResponseEntityExceptionHandler`, corpo único, sem stack trace | ✅ |
| `BusinessServiceException` + `ErroEnum` (código, chave, status) + `messages.properties` | ✅ |
| Virtual threads, shutdown gracioso, segurança de erro (`include-stacktrace=never`) | ✅ |
| Testes JUnit 5 `deve<Resultado>Quando<Condicao>`, preparar/executar/verificar, surefire em UTC | ✅ (só integração, por exigência do PDF) |
| Desvios (Liquibase, springdoc, MapStruct, Job, Security, Actuator) | ⚠️ explícitos e justificados em [`research.md` R9](../specs/001-intervalo-premios-produtores/research.md#r9-desvios-justificados-da-skill) |

### Itens avaliados e mantidos de propósito

| Item | Motivo |
|---|---|
| `LeituraArquivoFilmeServiceImpl` com ~240 linhas (acima das ~150 sugeridas) | Uma responsabilidade só (ler e validar o arquivo); dividir seria fragmentação (`solid.md`) |
| `identificarVencedor` devolve `null` num método privado | O VO incompleto é rejeitado na mesma classe e nunca atravessa camada |
| Arquivo que não é UTF-8 tem acentos trocados | Formato documentado como UTF-8 |
| Aviso de self-attach do Mockito no build | Inofensivo; o Mockito vem do `spring-boot-starter-test` e não é usado |
| Sequence com `allocationSize = 1` (gravação de 4 s para 20.000 filmes) | Regra da skill; ~70 ms no dataset do desafio, uma vez no startup |
| Pipeline do GitHub Actions não executada no GitHub | O repositório não foi publicado; o mesmo comando foi validado num Linux limpo (passada 3) |

## Histórico

### Passada 1 — revisão ao final da implementação (commit `3e9c712`)

| ID | Veredito | Severidade | Local | Problema | Situação |
|---|---|---|---|---|---|
| RV-1 | confirmado | Alta | `LeituraArquivoFilmeServiceImpl` | CSV UTF-8 com BOM (exportação do Excel) impedia a subida: o BOM grudava no nome da coluna `year`. | **Corrigido**; teste `ArquivoWindowsComBom`. |
| RV-2 | confirmado | Alta | `Dockerfile` | A imagem não copiava `lombok.config` e o container não subia (`No qualifying bean of type String`). | **Corrigido**; container validado. |
| RV-3 | confirmado | Média | índice git do `mvnw` | `mvnw` sem bit de execução; a CI em Linux falharia. | **Corrigido**; validado em container Linux. |
| RV-4 | confirmado | Média | `ImportacaoFilmeJob` | `ApplicationRunner` deixaria a API responder com o banco vazio durante a importação. | **Corrigido**: `SmartInitializingSingleton`. |
| RV-5 | confirmado | Baixa | `LeituraArquivoFilmeServiceImpl` | Campos interpretados duas vezes por linha. | **Corrigido**. |
| RV-6 | confirmado | Baixa | `ProdutorControllerTest` | `"Allow"` e `405` literais. | **Corrigido**. |
| RV-7 | confirmado | Baixa | `FilmeConstants` (removida depois, na passada 5) | Caractere BOM invisível no fonte. | **Corrigido**: constante `String.valueOf((char) 0xFEFF)`. |

### Passada 2 — auditoria sem alterar arquivos (commit `58b1bc1`) e passada 3 — correções

| ID | Classe | Problema (reproduzido na auditoria) | Correção | Evidência |
|---|---|---|---|---|
| R1 | Relevante | `1990;"Crocodile" Dundee II;...` abortava a importação inteira (`ARQUIVO_FILME_ILEGIVEL`), contrariando a política do clarify. | Commons CSV investigado em `jshell` (research R5): `trailingData(true)` remove as aspas sem aviso e `lenientEof(true)` engole as linhas seguintes — **ambos rejeitados**. O arquivo passou a ser lido linha a linha, com um parser por linha; aspas malformadas descartam só a linha, com WARN. | `ArquivoComAspasMalformadas`; WARN em `ImportacaoFilmeJobTest`; reexecução do CSV da auditoria |
| R2 | Relevante | Exportação da sessão desatualizada. | Regenerada do log real (`docs/ai/`). | `docs/ai/sessao-claude-code.md` |
| R3 | Relevante | Publicação pendente. | Fora do escopo técnico: a gestão do git é feita pelo usuário. | — |
| O1 | Opcional → corrigido | `Producer A AND Producer B` não era separado. | `and` como palavra inteira, sem diferenciar caixa, restrito ao separador; nomes seguem comparados exatamente. | `ArquivoSeparadorAndEmMaiusculas` (inclui `producer a` ≠ `Producer A` e "Anderson Andrade") |
| O3 | Opcional → corrigido | Linha repetida gerava intervalo 0 artificial. | Duplicata exata (após `strip` e caixa do `winner`) descartada com WARN citando a linha original; filmes distintos no mesmo ano mantêm intervalo 0. | `ArquivoComLinhasDuplicadas` (os dois casos) |
| O5 | Opcional → corrigido | Configuração sem uso. | `spring.sql.init.mode=embedded` removida (default). Mantidas, com justificativa no research R11: `server.shutdown` e `server.error.include-*` (default, mas exigidas pela skill) e as chaves `erro.arquivo-filme-*` (contrato do `ErroEnum`). | defaults conferidos no metadata do Boot 4.1.1 |
| O7 | Opcional → corrigido | Actuator sem consumidor real. | Dependência e propriedades `management.*` removidas; a validação do container usa o endpoint da API. Desvio da skill registrado no research R9. | `/actuator/health` → 404 no container |
| O9 | Opcional → corrigido | Imagens base com tag flutuante. | `eclipse-temurin:21.0.12.1_1-{jdk,jre}-noble@sha256:…` | log do `docker build` |
| O2 | Corrigido depois | Cabeçalho com outra caixa, outra ordem ou colunas extras impedia a subida. | Colunas localizadas pelo nome; teste `colunas-outra-ordem.csv`. | ✅ |
| O4, O6 | Opcional, mantido | arquivo não UTF-8; aviso do Mockito. | Ver "Itens avaliados e mantidos de propósito". | — |

Validações da passada 3:

| Validação | Resultado |
|---|---|
| `mvnw.cmd -B clean verify` (Windows, JDK 21.0.9) | 27 testes, 0 falhas, `BUILD SUCCESS` |
| `git archive HEAD` → `eclipse-temurin:21.0.12.1_1-jdk-noble` → `./mvnw -B verify` (simula a CI) | 27 testes, 0 falhas, `BUILD SUCCESS` |
| CSVs de borda da auditoria (fora do repositório) contra o jar novo | aspas no título: linha descartada e o resto importado; `AND`: separado; linha duplicada: descartada, sem intervalo 0; cabeçalho em maiúsculas: aceito após a correção de O2 |
| `docker build` com imagens fixadas | sucesso; log mostra `21.0.12.1_1-jdk/jre-noble@sha256:…` |
| `docker run` com o CSV padrão | uid 999; endpoint `200 application/json` com o JSON esperado; `/actuator/health` → 404 |
| `docker run` com volume e `FILME_ARQUIVO_CSV=file:/dados/aspas-malformadas.csv` | JSON do dataset; WARN das linhas 2 e 4 |
| `docker stop` | encerramento gracioso |

### Passada 4 — validação técnica final (sem operações de git)

| Verificação | Resultado |
|---|---|
| `mvnw.cmd -B clean verify` | 27 testes, 0 falhas, `BUILD SUCCESS` (antes e depois da remoção de código morto) |
| Oráculo independente: script Python escrito a partir das regras da spec, sem reaproveitar o código Java, comparado com a resposta HTTP | idêntico em `Movielist.csv`, `empates-mesmo-produtor.csv`, `duplicatas.csv`, `and-maiusculas.csv` e num CSV sintético de 20.000 filmes (24.767 vitórias, 22.767 intervalos, 1.177 empates no mínimo) |
| Desempenho (CSV sintético de 20.000 filmes, ~100× o original) | leitura e validação 0,4 s; gravação 4,0 s, uma vez no startup; consulta 25–90 ms. CSV original: ~11 ms por consulta |
| Execução local: `java -jar` com `FILME_ARQUIVO_CSV` | resultado do dataset informado |
| Docker: build com imagens fixadas; run com CSV padrão (200, JSON esperado, uid 999, 404, 405); run com outro CSV por volume; run com arquivo inexistente | todos conforme o esperado; arquivo inexistente encerra com exit 1 e `ARQUIVO_FILME_NAO_ENCONTRADO` |
| Código morto | removidos `StringUtil.STRING_VAZIA` (sem uso desde a troca do tratamento de BOM) e `BusinessServiceException.listaParametro` (nunca lido: os parâmetros já vão na mensagem da exceção) |

Avaliação de desempenho, só com problemas concretos: nenhum. A consulta é uma só (`JOIN` +
`ORDER BY`), sem N+1, e o cálculo é linear. O maior custo é a gravação no startup (ver "Itens
avaliados e mantidos de propósito"). Índice em `tx_produtor` é desnecessário para o volume; a FK em
`cd_filme` já é indexada pelo H2.

### Passada 5 — revisão de complexidade estrutural

Cada uma das 33 classes de produção foi avaliada (interfaces de implementação única, classes que
só delegam, Utils, camadas, exceções e constantes). A estrutura é o mínimo exigido pela skill: par
interface + `Impl` por camada, Facade para manter o I/O de arquivo fora da transação,
`ServiceTransactional` como único dono do `@Transactional`, comparação por Util, rotas de
constante, handler global e `ErroEnum`. Única mudança aplicada, aprovada pelo usuário:

| ID | Mudança | Motivo | Validação |
|---|---|---|---|
| R-1 | `FilmeConstants` removida; constantes como `private static final` em `LeituraArquivoFilmeServiceImpl` (32 classes de produção) | Único consumidor; a skill manda constante de uma classe só ficar no topo dela | `mvnw.cmd clean verify`: 27 testes, 0 falhas |

Avaliadas e não aplicadas por decisão do usuário: juntar o cálculo dos intervalos ao
`IntervaloPremioServiceImpl` e a separação de produtores à leitura do CSV (trade-off neutro).
