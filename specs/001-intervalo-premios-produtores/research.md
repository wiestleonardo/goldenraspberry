# Research: Intervalo de prêmios dos produtores

Decisões técnicas da fase 0, no formato Decisão / Razão / Alternativas. Fontes verificadas em
2026-10-09: Maven Central (versões), BOM `spring-boot-dependencies:4.1.1`, skill
`skill-claude-backend-spring`.

## R1. Versão de Java

- **Decisão**: Java 21.
- **Razão**: exigido pelo enunciado da tarefa e disponível no ambiente (`jdk-21.0.9`). A skill
  aceita 21 como mínimo; por isso vale a regra de não usar `synchronized` com I/O (o código não
  usa `synchronized`).
- **Alternativas**: Java 25 (preferido pela skill para projeto novo) — não instalado e exige
  autorização para mudar a versão do projeto.

## R2. Spring Boot

- **Decisão**: Spring Boot 4.1.1 (última versão estável; `4.2.0-M2` é milestone).
- **Razão**: linha corrente recomendada pela skill; Spring Framework 7, Hibernate 7.4, Jackson 3,
  JUnit Jupiter. Starters por tecnologia: `spring-boot-starter-webmvc`,
  `spring-boot-starter-data-jpa`, `spring-boot-starter-actuator`; testes:
  `spring-boot-starter-test`, `spring-boot-resttestclient`.
- **Alternativas**: 4.0.x (mais antiga, sem vantagem); 3.5.x (fora da stack da skill).

## R3. Persistência: Spring Data JPA (escrita) + `@Query` nativa (leitura)

- **Decisão**: entidades `Filme` e `ProdutorFilme` (JPA), gravadas com `saveAll` em cascata numa
  única transação; leitura por **uma** `@Query(nativeQuery = true)` em text block no
  `FilmeRepository`, devolvendo a Projection `VitoriaProdutorProjection(produtor, ano)` já
  ordenada por produtor e ano, filtrando `fl_vencedor = TRUE`.
- **Razão**: `database.md` indica Spring Data como caminho padrão e "`@Query(nativeQuery = true)`
  com text block" para SQL nativo fixo e curto — exatamente a consulta de leitura. Uma consulta,
  sem N+1, projeção parcial (só as duas colunas necessárias), ordenação feita pelo banco.
- **Alternativas**:
  - `JdbcClient` em `RepositoryImpl` para tudo: exigiria SQL de `INSERT` manual e batch manual
    (o `JdbcClient` não tem batch); mais código sem ganho.
  - QueryDSL: dependência e processador de anotação extras para uma única consulta fixa (YAGNI);
    risco de compatibilidade com Hibernate 7 apontado na própria skill.
  - Calcular tudo em memória durante a leitura do CSV sem banco: viola o requisito do PDF
    (inserir os dados numa base ao iniciar).
  - Tabela `tb_produtor` normalizada (produtor único + N:N): sem consumidor para o id do
    produtor; `tb_produtorfilme` com o nome basta para a regra.

## R4. Schema

- **Decisão**: `schema.sql` explícito (sequences `gen_filme`, `gen_produtorfilme`; tabelas
  `tb_filme`, `tb_produtorfilme`; PK `<tabela>_pk`; FK `produtorfilme_filme_fk`) executado pela
  inicialização de SQL do Boot (padrão para banco embarcado), com
  `spring.jpa.hibernate.ddl-auto=validate` conferindo entidade × schema a cada subida.
- **Razão**: nomenclatura de `database.md` (`tb_`, `id_`, `cd_`, `nr_`, `tx_`, `fl_`, `gen_`) sem
  depender de geração do Hibernate. Sem índice extra: a FK do H2 já cria índice em `cd_filme` e o
  volume não justifica outros (constitution: evitar índices desnecessários).
- **Alternativas**: Liquibase (padrão da skill) — sem histórico de migração a preservar, o banco é
  recriado a cada execução; desvio registrado em R9. `ddl-auto=create` — esconde o DDL e foge da
  nomenclatura.

## R5. Leitura do CSV

- **Decisão**: Apache Commons CSV 1.14.1, `;` como delimitador, aspas `"`, linhas vazias
  ignoradas, cabeçalho lido da primeira linha e comparado exatamente com
  `year;title;studios;producers;winner` (BOM UTF-8 removido do primeiro nome). Arquivo resolvido
  pelo `ResourceLoader` do Spring a partir da propriedade `filme.arquivo-csv`
  (padrão `classpath:Movielist.csv`; aceita `file:/caminho.csv`; variável de ambiente
  `FILME_ARQUIVO_CSV`).
- **Razão**: biblioteca consolidada, trata aspas e separadores dentro de campos; `split(";")` à mão
  falharia com campo entre aspas contendo `;`.
- **Alternativas**: OpenCSV (mais pesada, reflexão), parser próprio (reinventa casos de aspas).

## R6. Validação e erros de importação (clarify 2026-10-09)

- **Decisão**:
  - Linha inválida (colunas ≠ 5, ano ausente/não numérico/≤ 0, título vazio, nenhum produtor,
    `winner` diferente de `yes`/vazio sem diferenciar caixa) → `log.warn` com número da linha e
    motivo; linha descartada; importação continua. Ao final, `log.info` com o total importado e
    descartado.
  - Arquivo inexistente/ilegível ou cabeçalho incompatível → `BusinessServiceException` com
    `ErroEnum` (`ARQUIVO_FILME_NAO_ENCONTRADO`, `ARQUIVO_FILME_ILEGIVEL`,
    `CABECALHO_ARQUIVO_FILME_INVALIDO`), propagada pelo `ImportacaoFilmeJob`: a aplicação não sobe.
- **Razão**: decisão do usuário no clarify; nenhuma linha some sem registro.
- **Alternativas**: fail-fast para qualquer linha (rejeitada pelo usuário); tabela de rejeitados
  (YAGNI, vetada pelo usuário).

## R7. Separação de produtores

- **Decisão**: expressão `\s*,\s*(?:and\s+)?|\s+and\s+` (constante em `RegexConstants`, `Pattern`
  compilado uma vez), seguida de `strip()` em cada nome, descarte de fragmentos vazios e
  `distinct()` preservando a ordem. Comparação de nomes exata, sem ignorar caixa.
- **Razão**: cobre `A, B`, `A and B`, `A, B and C`, `A, B, and C` (caso real de 2019 no CSV) e
  espaços excedentes; `and` só é separador entre espaços, então nomes como "Alexandra" ou
  "Anderson" não são quebrados. A comparação exata vem do clarify.
- **Alternativas**: separar só por vírgula e ` and ` literais — falha com `, and` e com espaços
  duplicados.

## R8. Algoritmo e complexidade

- **Decisão**:
  1. Banco devolve as W vitórias (pares produtor × ano de filme vencedor) ordenadas por
     `tx_produtor, nr_ano` — O(W log W).
  2. `CalculoIntervaloPremioServiceApplication` percorre a lista uma vez: quando a linha atual é do
     mesmo produtor da anterior, gera o intervalo `ano - anoAnterior` — O(W). Isso garante que só
     vitórias consecutivas formam intervalos e que anos repetidos geram intervalo 0.
  3. Calcula o menor e o maior valor de intervalo e filtra os itens iguais a cada um — O(I),
     I ≤ W.
  4. Ordena cada lista de empates por `producer`, `previousWin`, `followingWin` (ordem natural de
     `String` e de `Long`) — O(E log E), E empates.
  Total: importação O(N) + consulta O(W log W). Memória O(W).
- **Razão**: correto por construção (agrupamento contíguo garantido pelo `ORDER BY`), linear após a
  ordenação, sem consultas em laço. A ordenação final em Java torna a saída determinística
  independentemente da collation do banco.
- **Determinismo da agrupação**: o H2 compara `VARCHAR` com diferenciação de caixa (collation
  padrão), portanto nomes que diferem só em caixa não se intercalam. Coberto por teste.
- **Alternativas**: agrupar em `Map<String, List<Long>>` em Java — mesma complexidade, mais
  código; calcular intervalos com função de janela `LAG` no SQL — mais acoplado ao banco e menos
  legível; cache do resultado — dados imutáveis após o startup, mas a consulta já é barata (YAGNI).

## R9. Desvios justificados da skill

| Regra da skill | Desvio | Justificativa |
|---|---|---|
| Liquibase versiona o schema | `schema.sql` | Banco em memória recriado a cada subida; nenhum histórico de migração. Menos uma dependência. |
| OpenAPI (springdoc) no Controller | Sem springdoc | Contrato fixo pelo PDF, um endpoint; evita dependência e endpoints extras (`/swagger-ui`, `/v3/api-docs`). |
| MapStruct acima de 3 atributos | Conversão manual (`FilmeVO` → `Filme`) num método privado do `ServiceTransactional` | Um único mapeamento; a skill usa "preferir", não "obrigatório"; evita processador de anotação. |
| Biblioteca compartilhada de Utils e `BusinessServiceException` | Criados localmente (`LongUtil`, `ObjectUtil`, `StringUtil`, `BusinessServiceException` + `ErroEnum`) com o mínimo usado | A biblioteca não existe neste projeto; a skill orienta criar com os mesmos nomes. |
| `Job` é `@Scheduled` | `ImportacaoFilmeJob` implementa `SmartInitializingSingleton` | É a entrada sem HTTP que roda uma vez no startup; papel equivalente ao do Job (só delega à Facade). Escolhido no lugar de `ApplicationRunner` porque roda **antes** de o Tomcat aceitar conexões (verificado no log: importação concluída antes de "Tomcat started"); com o runner haveria uma janela em que a API responderia com o banco vazio. |
| Datas com `Clock` / fuso | Não aplicável | Não há data-hora; o "ano" é número do CSV. |
| Bean Validation no DTO de entrada | Não aplicável | O endpoint não recebe corpo nem parâmetros. |
| Segurança (`SecurityFilterChain`) | Sem Spring Security | Dado público, endpoint só de leitura, PDF não exige autenticação; nenhum dado pessoal sensível além de nomes públicos de produtores. |

## R10. Testes de integração

- **Decisão**: `ProdutorControllerTest` com `@SpringBootTest(webEnvironment = RANDOM_PORT)`,
  `@AutoConfigureRestTestClient` e uma classe `@Nested` por dataset
  (`@TestPropertySource(properties = "filme.arquivo-csv=classpath:dataset/<cenario>.csv")`);
  cada contexto tem H2 próprio (nome único gerado pelo Boot), então não há vazamento entre
  cenários. As respostas são comparadas com JSON esperado escrito à mão em modo estrito (ordem e
  campos exatos). `ImportacaoFilmeJobTest` sobe a aplicação via `SpringApplicationBuilder` para
  verificar falha de inicialização (arquivo inexistente, cabeçalho inválido) e os WARN de linha
  inválida (`OutputCaptureExtension`).
- **Razão**: o PDF exige somente testes de integração; o teste atravessa HTTP real, Spring, CSV,
  H2 e regra.
- **Alternativas**: `@WebMvcTest`/`MockMvcTester` (fatia web com mock — não exercita importação nem
  banco); Testcontainers (desnecessário: o banco exigido é embarcado).

## R11. Operação

- **Decisão**: `spring.threads.virtual.enabled=true`, `spring.jpa.open-in-view=false`,
  `server.shutdown=graceful`, `server.error.include-stacktrace=never`, Actuator expondo só
  `health` (com probes). Dockerfile multi-stage `eclipse-temurin:21-jdk` → `eclipse-temurin:21-jre`,
  usuário não root, porta 8080. CI: `actions/setup-java` (Temurin 21, cache Maven) +
  `./mvnw -B verify`.
- **Razão**: propriedades essenciais de `stack-and-build.md` e `cloud-native.md` aplicáveis ao caso.
- **Alternativas**: imagem nativa GraalVM (complexidade sem necessidade); `docker build` na CI
  (fora do escopo pedido para a pipeline).
