---
description: "Lista de tarefas da feature 001-intervalo-premios-produtores"
---

# Tasks: Intervalo de prêmios dos produtores (Golden Raspberry Awards)

**Input**: Design documents from `specs/001-intervalo-premios-produtores/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/intervalos-premios.md

**Tests**: o PDF exige **somente testes de integração** (constitution V). Não há testes unitários.
As expectativas de cada dataset são calculadas à mão e registradas no próprio teste.

**Organization**: tarefas por história de usuário (US1, US2 — P1; US3 — P2).

Abreviações de caminho:

- `J/` = `src/main/java/br/com/outsera/goldenraspberry/`
- `T/` = `src/test/java/br/com/outsera/goldenraspberry/`
- `R/` = `src/main/resources/`
- `TR/` = `src/test/resources/`

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: história atendida (US1, US2, US3)

---

## Phase 1: Setup

**Purpose**: projeto Maven compilável, versionamento e configuração base.

- [X] T001 Criar `.gitignore` (Java, Maven `target/`, IDEs, temporários, `.specify/feature.json`, cópia local `skill-claude-backend-spring/` da raiz) em `.gitignore`
- [X] T002 Criar `pom.xml` com parent `spring-boot-starter-parent` 4.1.1, `java.version` 21, dependências `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`, `org.apache.commons:commons-csv` (versão em propriedade `dependency.version.commons-csv` = 1.14.1), `h2` (runtime), `lombok` (optional), testes `spring-boot-starter-test` e `spring-boot-resttestclient`; `maven-compiler-plugin` com `parameters` e Lombok em `annotationProcessorPaths`; surefire com `-Duser.timezone=UTC`, em `pom.xml`
- [X] T003 Gerar Maven Wrapper (`mvn wrapper:wrapper`) em `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`
- [X] T004 [P] Criar `lombok.config` com `config.stopBubbling = true` e `lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Value` em `lombok.config`
- [X] T005 [P] Criar classe principal `GoldenRaspberryApplication` (pacote raiz) em `J/GoldenRaspberryApplication.java`
- [X] T006 [P] Criar `R/application.properties` em blocos (`#Aplicacao`, `#Arquivo de filmes`, `#JPA/Hibernate`, `#Servidor`, `#Actuator`): `spring.threads.virtual.enabled=true`, `filme.arquivo-csv=${FILME_ARQUIVO_CSV:classpath:Movielist.csv}`, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, batch de inserção, `server.shutdown=graceful`, `server.error.include-stacktrace=never`, `management.endpoints.web.exposure.include=health`, probes; sem URL de datasource (nome único gerado pelo Boot)
- [X] T007 [P] Copiar `challenge/Movielist.csv` sem alteração para `R/Movielist.csv`

---

## Phase 2: Foundational (bloqueia todas as histórias)

- [X] T008 Criar `R/schema.sql` conforme data-model.md: sequences `gen_filme`, `gen_produtorfilme` (incremento 1); `tb_filme` (`id_filme BIGINT NOT NULL`, `nr_ano BIGINT NOT NULL`, `tx_titulo VARCHAR NOT NULL`, `tx_estudios VARCHAR`, `tx_produtores VARCHAR NOT NULL`, `fl_vencedor BOOLEAN NOT NULL`, `filme_pk`); `tb_produtorfilme` (`id_produtorfilme BIGINT NOT NULL`, `cd_filme BIGINT NOT NULL`, `tx_produtor VARCHAR NOT NULL`, `produtorfilme_pk`, `produtorfilme_filme_fk`)
- [X] T009 [P] Criar entidade `Filme` (`@Getter @Setter`, `Serializable`, `@SequenceGenerator(allocationSize = 1)`, `@OneToMany(mappedBy = "filme", cascade = PERSIST)` `listaProdutorFilme`, equals/hashCode só pelo id) em `J/repository/domain/Filme.java`
- [X] T010 [P] Criar entidade `ProdutorFilme` (`@ManyToOne(fetch = LAZY)` `filme` em `cd_filme`, `produtor` em `tx_produtor`) em `J/repository/domain/ProdutorFilme.java`
- [X] T011 [P] Criar Utils locais mínimos (`final`, construtor privado): `LongUtil` (`toLong`, `igual`, `menor`, `maior`, `menos` com `Math.subtractExact`), `ObjectUtil` (`isNull`, `isNotNull`, `isEquals`), `StringUtil` (`STRING_VAZIA`, `isNullOrEmpty`, `notEmpty`) em `J/util/`
- [X] T012 [P] Criar `ErroEnum` (`codigo`, `HttpStatus`; `ARQUIVO_FILME_NAO_ENCONTRADO`, `ARQUIVO_FILME_ILEGIVEL`, `CABECALHO_ARQUIVO_FILME_INVALIDO`, `RECURSO_NAO_ENCONTRADO`, `METODO_NAO_SUPORTADO`, `REQUISICAO_INVALIDA`, `ERRO_NAO_TRATADO`) em `J/enumerator/ErroEnum.java`, `BusinessServiceException` (unchecked, carrega `ErroEnum` e parâmetros, preserva causa) em `J/exception/BusinessServiceException.java` e textos em `R/messages.properties`
- [X] T013 [P] Criar `RotaConstants` (`CONTEXTO = "/api"`, classe interna `Produtor` com `BASE` e `INTERVALOS_PREMIOS = "/intervalosPremios"`), `RegexConstants` (`SEPARADOR_PRODUTOR`) e `FilmeConstants` (cabeçalho esperado, indicador `yes`, quantidade de colunas) em `J/constants/`

**Checkpoint**: `./mvnw -q compile` passa e o contexto sobe com o schema validado.

---

## Phase 3: User Story 1 — Consultar intervalos mínimo e máximo (P1) 🎯 MVP

**Goal**: `GET /api/produtores/intervalosPremios` devolve o JSON exato do PDF para o CSV original.

**Independent Test**: subir com o CSV padrão e comparar a resposta com o JSON calculado à mão.

- [X] T014 [P] [US1] Criar `SeparacaoProdutorServiceApplication` + `Impl` (`separar(String produtores)` → `List<String>`; `Pattern` de `RegexConstants`, `strip()`, sem vazios, `distinct()`, caixa preservada) em `J/service/application/`
- [X] T015 [P] [US1] Criar `FilmeVO` (`record`: `ano`, `titulo`, `estudios`, `produtores`, `vencedor`, `listaProdutor`) em `J/vo/FilmeVO.java`
- [X] T016 [US1] Criar `LeituraArquivoFilmeService` + `Impl` (resolve `filme.arquivo-csv` pelo `ResourceLoader`, valida cabeçalho, converte linhas válidas em `FilmeVO`) em `J/service/` e `J/service/impl/`
- [X] T017 [US1] Criar `FilmeServiceTransactional` + `Impl` (`salvar(List<FilmeVO>)` com `@Transactional(rollbackFor = Exception.class)`, monta `Filme`/`ProdutorFilme`, `saveAll`) em `J/service/transactional/`
- [X] T018 [US1] Criar `ImportacaoFilmeFacade` + `Impl` (lê fora da transação, grava pelo `ServiceTransactional`, log do resumo) em `J/service/facade/` e `ImportacaoFilmeJob` (`SmartInitializingSingleton` — roda antes de o servidor HTTP aceitar conexões; só delega) em `J/job/ImportacaoFilmeJob.java`
- [X] T019 [P] [US1] Criar `VitoriaProdutorProjection` (`record`: `produtor`, `ano`) em `J/repository/projection/` e `FilmeRepository` (`CrudRepository<Filme, Long>` com `listarVitoriaProdutor()` — `@Query(nativeQuery = true)` em text block, caixa alta, `WHERE filme.fl_vencedor = TRUE ORDER BY produtorfilme.tx_produtor, filme.nr_ano`) em `J/repository/FilmeRepository.java`
- [X] T020 [P] [US1] Criar DTOs `record` `IntervaloPremioDTO` (`@JsonProperty` `producer`, `interval`, `previousWin`, `followingWin`) e `RetornoIntervaloPremioDTO` (`min`, `max`) em `J/dto/`
- [X] T021 [US1] Criar `CalculoIntervaloPremioServiceApplication` + `Impl` (percurso linear de pares consecutivos, filtro de empates de mínimo e máximo, ordenação por `producer`, `previousWin`, `followingWin`) em `J/service/application/`
- [X] T022 [US1] Criar `IntervaloPremioService` + `Impl` (`adquirir()` → repository + cálculo) em `J/service/`
- [X] T023 [US1] Criar `ProdutorController` (interface com `@RequestMapping(RotaConstants.Produtor.BASE)` e `@GetMapping(RotaConstants.Produtor.INTERVALOS_PREMIOS)`) em `J/api/` e `ProdutorControllerImpl` (`@RestController`, log de entrada, `ResponseEntity.ok`) em `J/api/impl/`
- [X] T024 [US1] Criar `ErroDTO` em `J/dto/ErroDTO.java` e `ApiExceptionHandler` (`@RestControllerAdvice`, estende `ResponseEntityExceptionHandler`, `handleExceptionInternal` com `@Override`, handler de `Exception` → 500 com `log.error`) em `J/config/ApiExceptionHandler.java`
- [X] T025 [US1] Criar `ProdutorControllerTest` (`@SpringBootTest(RANDOM_PORT)`, `@AutoConfigureRestTestClient`) com cenário do CSV original (JSON estrito), contrato (`Content-Type`, campos exatos), 404 e 405 com `ErroDTO` em `T/api/ProdutorControllerTest.java`

**Checkpoint**: MVP — `./mvnw verify` verde e endpoint correto com o CSV original.

---

## Phase 4: User Story 2 — Datasets desconhecidos (P1)

**Goal**: resultados corretos para qualquer dataset no layout do CSV.

**Independent Test**: cada `@Nested` sobe com um dataset próprio e compara com o JSON esperado à mão.

- [X] T026 [P] [US2] Criar datasets em `TR/dataset/`: `consecutivas.csv`, `tres-vitorias.csv`, `empate-minimo.csv`, `empate-maximo.csv`, `empates-mesmo-produtor.csv`, `multiplos-produtores.csv` (separadores mistos, aspas, espaços, `, and`, nome repetido no filme), `nao-vencedores.csv`, `anos-repetidos.csv`, `sem-intervalos.csv`, `ordem-aleatoria.csv`, `caixa-diferente.csv`, `intervalo-unico.csv`, `somente-cabecalho.csv` e, acrescentado na revisão, `windows-crlf-bom.csv`
- [X] T027 [US2] Acrescentar a `T/api/ProdutorControllerTest.java` um `@Nested` por dataset de T026 (`@TestPropertySource(properties = "filme.arquivo-csv=classpath:dataset/<arquivo>")`), com JSON esperado calculado à mão e comparação estrita

**Checkpoint**: todos os cenários do item 10 do prompt cobertos e verdes.

---

## Phase 5: User Story 3 — Importação previsível (P2)

**Goal**: linhas inválidas descartadas com WARN; erro estrutural impede a subida.

**Independent Test**: subir com dataset de linhas inválidas (resultado só com válidas) e com arquivo ausente/cabeçalho inválido (falha).

- [X] T028 [P] [US3] Criar `TR/dataset/linhas-invalidas.csv` (ano não numérico, colunas a menos, título vazio, produtores vazios, `winner` desconhecido, linha em branco) e `TR/dataset/cabecalho-invalido.csv`
- [X] T029 [US3] Acrescentar `@Nested` de linhas inválidas a `T/api/ProdutorControllerTest.java` (resultado considera só as válidas)
- [X] T030 [US3] Criar `T/job/ImportacaoFilmeJobTest.java` (`SpringApplicationBuilder` + `OutputCaptureExtension`): falha com arquivo inexistente, falha com cabeçalho inválido, WARN com número da linha e motivo para cada linha inválida, substituição por `file:` (arquivo temporário fora do classpath)

**Checkpoint**: `./mvnw verify` verde com as três histórias.

---

## Phase 6: Polish & Cross-Cutting

- [X] T031 [P] Criar `Dockerfile` multi-stage (`eclipse-temurin:21-jdk` build com wrapper → `eclipse-temurin:21-jre`, usuário não root, `EXPOSE 8080`) e `.dockerignore`
- [X] T032 [P] Criar `.github/workflows/ci.yml` (push/PR, `actions/checkout`, `actions/setup-java` Temurin 21 com cache Maven, `./mvnw -B verify`)
- [X] T033 Validar Docker de fato: build, run, `curl` do endpoint, JSON, stop — registrar evidência
- [X] T034 [P] Criar `README.md` (objetivo, stack, pré-requisitos, execução local, testes, Docker, endpoint, exemplo JSON, troca do CSV, arquitetura, decisões, padrões com link para `.claude/skills/skill-claude-backend-spring/SKILL.md`, evidências de IA)
- [X] T035 [P] Criar `docs/ai/` (ferramentas, skills, prompt real, decisões, problemas e correções, validações, exportação sanitizada da sessão)
- [X] T036 Revisão final contra a skill e o PDF (achados por severidade, correções, nova execução de `./mvnw verify`)

---

## Phase 7: Correções pós-auditoria (2026-10-09)

**Purpose**: corrigir os achados da auditoria técnica (R1, O1, O3, O5, O7, O9, R2) antes da publicação.

- [X] T037 Investigar o Commons CSV 1.14.1 (padrão, `trailingData`, `lenientEof`, parse por linha) e registrar em `specs/001-intervalo-premios-produtores/research.md` (R5)
- [X] T038 [US3] Ler o arquivo linha a linha em `J/service/impl/LeituraArquivoFilmeServiceImpl.java`: aspas malformadas descartam só a linha com WARN; dataset `TR/dataset/aspas-malformadas.csv` e teste `ArquivoComAspasMalformadas`
- [X] T039 [US2] Separador `and` como palavra inteira em qualquer caixa em `J/constants/RegexConstants.java`; dataset `TR/dataset/and-maiusculas.csv` e teste `ArquivoSeparadorAndEmMaiusculas`
- [X] T040 [US2] Descartar duplicata exata (WARN com a linha original) preservando intervalo 0 de filmes distintos; dataset `TR/dataset/duplicatas.csv` e teste `ArquivoComLinhasDuplicadas`
- [X] T041 [US3] Teste de WARN para aspas malformadas e duplicatas em `T/job/ImportacaoFilmeJobTest.java`
- [X] T042 [P] Remover `spring.sql.init.mode` (default) de `R/application.properties`; manter as propriedades exigidas pela skill
- [X] T043 [P] Remover `spring-boot-starter-actuator` de `pom.xml` e as propriedades `management.*`; validar o container pelo endpoint da API
- [X] T044 [P] Fixar as imagens base por versão e digest no `Dockerfile`
- [X] T045 Atualizar README, `docs/revisao-tecnica.md`, `docs/ai/` (exportação regenerada do log real) e os artefatos desta spec

---

## Dependencies & Execution Order

- Setup (T001–T007) → Foundational (T008–T013) → US1 (T014–T025) → US2 (T026–T027) e US3
  (T028–T030) → Polish (T031–T036).
- US2 e US3 dependem apenas do código de US1 (só acrescentam datasets e testes); podem seguir em
  paralelo entre si.
- Dentro de US1: T014/T015/T019/T020 em paralelo → T016 → T017 → T018; T021 → T022 → T023 → T024 → T025.

## Parallel Example: User Story 1

```text
T014 SeparacaoProdutorServiceApplication | T015 FilmeVO | T019 Projection + Repository | T020 DTOs
```

## Implementation Strategy

1. MVP: Setup + Foundational + US1, validar com o CSV original.
2. US2: datasets alternativos (maior risco de avaliação).
3. US3: robustez da importação.
4. Polish: Docker, CI, README, evidências de IA, revisão final.
