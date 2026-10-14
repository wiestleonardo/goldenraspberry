# Implementation Plan: Intervalo de prêmios dos produtores (Golden Raspberry Awards)

**Branch**: `001-intervalo-premios-produtores` | **Date**: 2026-10-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-intervalo-premios-produtores/spec.md`

## Summary

API REST (nível 2 de Richardson) que, na inicialização, importa o CSV de indicados ao Pior Filme
para um H2 em memória e expõe `GET /api/produtores/intervalosPremios`, devolvendo os produtores com
menor e maior intervalo entre vitórias consecutivas no formato exato do PDF. A importação lê o
arquivo (caminho configurável) com commons-csv, valida linha a linha (descarta inválidas com WARN,
falha em erro estrutural), separa produtores e persiste filmes e produtores por JPA em uma única
transação. A consulta faz **um** `SELECT` nativo com `JOIN` ordenado por produtor e ano, e um
`ServiceApplication` puro percorre a lista linearmente para montar os intervalos e filtrar os
empates de mínimo e máximo. Detalhes e alternativas em [research.md](research.md).

## Technical Context

**Language/Version**: Java 21 (Temurin/Oracle JDK 21; Java 25 não adotado — ver research R1)

**Primary Dependencies**: Spring Boot 4.1.1 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`),
Hibernate 7.4 (do Boot), Apache Commons CSV 1.14.1. Sem Lombok (research R9). Actuator removido após a
auditoria (research R9)

**Storage**: H2 2.4 embarcado em memória, nome único por contexto; schema por `schema.sql`

**Testing**: JUnit 5 + `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestTestClient`
(`spring-boot-resttestclient`) — somente testes de integração, com datasets CSV dedicados

**Target Platform**: JVM 21 em Linux (container Docker) ou Windows/macOS local

**Project Type**: web-service (um módulo Maven)

**Performance Goals**: consulta < 1 s com o dataset original (SC-006); importação em poucos
segundos para milhares de linhas

**Constraints**: sem instalação externa; importação só no startup; sem N+1; cálculo
O(N + W log W); JSON exatamente como o PDF

**Scale/Scope**: dataset original 206 filmes / 42 vencedores; datasets de avaliação de tamanho
semelhante; um endpoint

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação | Situação |
|---|---|---|
| I. PDF é autoridade | CSV no startup, H2 em memória, Richardson nível 2, contrato JSON da pág. 2, só testes de integração, README | OK |
| II. Skill obrigatória | Pacotes/camadas/nomes da skill; par interface + `Impl`; construtor + `final`; `this.`; SQL caixa alta; `@Transactional` só no `ServiceTransactional`; logger SLF4J; handler global; testes `deve...Quando...` | OK — desvios justificados em research R9 |
| III. Simplicidade | Um módulo; sem cache, paralelismo, mensageria, springdoc, MapStruct, QueryDSL, Liquibase | OK |
| IV. Correção/determinismo | Intervalos só consecutivos; empates preservados; ordenação determinística pelo `ORDER BY`; nada fixo | OK |
| V. Só integração | Nenhum teste unitário; contexto real + HTTP real + H2; expectativas calculadas à mão | OK |
| VI. Reprodutibilidade | Java 21, Boot 4.1.1, Maven Wrapper, Docker multi-stage não root, CI GitHub Actions | OK |
| VII. Rastreabilidade IA | `docs/ai/` com prompt real, decisões e exportação de sessão sanitizada | OK |

Re-check pós-design (Phase 1): sem novas violações. A Facade de importação e o
`ServiceApplication` de cálculo existem porque há orquestração real (leitura de arquivo fora da
transação + escrita transacional) e cálculo puro, conforme `layers-and-naming.md`.

## Project Structure

### Documentation (this feature)

```text
specs/001-intervalo-premios-produtores/
├── plan.md
├── research.md
├── data-model.md
└── tasks.md            (gerado por /speckit-tasks)

contracts/, quickstart.md e checklists/ foram gerados nesta fase e depois incorporados: contrato e
quickstart ao README.md, resultado do checklist ao spec.md.
```

### Source Code (repository root)

```text
pom.xml  mvnw  mvnw.cmd  .mvn/wrapper/
Dockerfile  .dockerignore  .gitignore  README.md
.github/workflows/ci.yml
docs/revisao-tecnica.md
docs/ai/README.md  sessao-claude-code.md  exportar_sessao.py
src/main/java/br/com/outsera/goldenraspberry/
├── Application.java
├── api/ProdutorController.java                      (contrato HTTP)
│   └── impl/ProdutorControllerImpl.java
├── config/ApiExceptionHandler.java                  (@RestControllerAdvice)
├── constants/RotaConstants.java  RegexConstants.java
├── dto/RetornoIntervaloPremioDTO.java  IntervaloPremioDTO.java  ErroDTO.java
├── enumerator/ErroEnum.java
├── exception/BusinessServiceException.java
├── job/ImportacaoFilmeJob.java                      (SmartInitializingSingleton — entrada sem HTTP)
├── repository/FilmeRepository.java
│   ├── domain/Filme.java  ProdutorFilme.java
│   └── projection/VitoriaProdutorProjection.java
├── service/LeituraArquivoFilmeService.java  IntervaloPremioService.java
│   ├── impl/LeituraArquivoFilmeServiceImpl.java  IntervaloPremioServiceImpl.java
│   ├── facade/ImportacaoFilmeFacade.java + impl/ImportacaoFilmeFacadeImpl.java
│   ├── transactional/FilmeServiceTransactional.java + impl/FilmeServiceTransactionalImpl.java
│   └── application/SeparacaoProdutorServiceApplication.java  CalculoIntervaloPremioServiceApplication.java
│       └── impl/...Impl.java
├── util/LongUtil.java  ObjectUtil.java  StringUtil.java
└── vo/FilmeVO.java
src/main/resources/
├── application.properties  messages.properties  schema.sql
└── Movielist.csv                                     (cópia fiel do CSV original do desafio)
src/test/java/br/com/outsera/goldenraspberry/
├── api/ProdutorControllerTest.java                   (@Nested por dataset, HTTP real)
└── job/ImportacaoFilmeJobTest.java                   (falhas estruturais e WARN de linha inválida)
src/test/resources/dataset/*.csv
```

**Structure Decision**: projeto único Maven com a árvore de pacotes da skill (`SKILL.md`,
"Estrutura de pacotes padrão"), criando apenas os pacotes com responsabilidade real. Testes ficam
no pacote da classe exercitada pela fronteira (Controller para o contrato HTTP, Job para a
importação).

## Complexity Tracking

Nenhuma violação da constitution. Desvios pontuais de convenções da skill, todos de simplificação,
estão em [research.md](research.md) (R9).
