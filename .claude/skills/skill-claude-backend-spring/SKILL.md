---
name: skill-claude-backend-spring
description: Team conventions for Java/Spring Boot backends (Spring Boot 4.x, Spring Framework 7, Java 21+). Use for any Java file in a Spring project: creating a controller, service or class, choosing its package, layer and name (Controller/Facade/Service/ServiceTransactional/ServiceApplication/ServiceSingleton/Repository/RepositoryCustom/IntercommService/Client/Publisher/Job/DTO/VO/Projection/Mapper), constructor injection and injection direction, @Transactional placement, data types, null-safe comparisons and math via Util, logging with @Slf4j, BusinessServiceException and @RestControllerAdvice error mapping, constants, Spring Data/QueryDSL/JdbcClient/native SQL, JPA mapping, database naming, Liquibase, HTTP clients (@HttpExchange), resilience (@Retryable), configuration (@ConfigurationProperties, profiles), Spring Security, tests (JUnit 5, Mockito, @WebMvcTest, @DataJpaTest, Testcontainers), GraalVM native/AOT, SOLID, concurrency and virtual threads, cloud-native scale (GCP/AWS), security and code review. Also use when migrating a Spring Boot 2.x/3.x project, or when the user asks where a class should live, how to name it, or whether code is secure, thread-safe, ready to scale or follows the team standard.
---

# Convenções de Backend Java/Spring Boot

## Overview

Convenções de arquitetura e estilo para backends Java/Spring Boot, extraídas dos serviços Spring em produção (PDV, documento eletrônico, periféricos, agente, repositório compartilhado, PIX) e atualizadas para a stack atual.

**Stack-alvo:** Spring Boot **4.x** (Spring Framework 7, Jakarta EE 11, Hibernate 7, Jackson 3), **Java 21 no mínimo — Java 25 (LTS) em projeto novo**. Spring MVC **imperativo/bloqueante** sobre virtual threads — sem WebFlux, sem `Mono`/`Flux` em código de negócio. Os projetos existentes estão em Boot 2.7, 3.2, 3.4 e 3.5; o que muda numa migração está em `stack-and-build.md`.

Persistência, por um destes caminhos (detalhe em `database.md`):

- **Spring Data JPA** — `CrudRepository` com método derivado para a consulta simples de uma tabela. O mais comum na base.
- **`RepositoryCustom` + `RepositoryImpl`** — consulta dinâmica ou com `JOIN`/agregação, por QueryDSL (padrão da base) ou `JdbcClient` com SQL nativo.
- **`@Query(nativeQuery = true)` com text block** — SQL nativo fixo, curto, sem montagem dinâmica.

**Fluxo de injeção, direção única:** `Controller (ou Job) → Facade → Service / ServiceTransactional → Repository / IntercommService / Publisher`. A Facade é opcional (só quando há orquestração); as três da ponta nunca injetam para cima nem de lado. Detalhe e casos de borda em `layers-and-naming.md`.

> **Atenção, quem vem da skill Quarkus:** aqui **Facade é o orquestrador** que o Controller chama (é o que a base Spring sempre fez), não a chamada externa. A chamada externa é o **`IntercommService`**, que usa um **`Client`** `@HttpExchange`. A injeção é **por construtor** e o `this.` vai em **todo** acesso a membro da classe. As demais regras (idioma, tipos, Util, SQL, segurança, escala) são as mesmas.

## Como aplicar esta skill

**As regras daqui são o padrão da equipe, não sugestões.** Aplique-as em todo código que escrever ou revisar, inclusive nos detalhes que parecem pequenos (nome de variável, `this.`, linha em branco, Util no lugar de operador): são esses detalhes que fazem o código parecer do time, e a revisão vai cobrá-los.

- **O código existente não é o padrão.** A base tem 6 mil `@Autowired` em campo, JUnit 4, Swagger 2, ModelMapper e `java.util.Date` — tudo isso é passado, não modelo. Antes de copiar um estilo do projeto, confira "Divergências conhecidas — não copiar", no fim deste arquivo. Na dúvida, código novo segue a skill — exceto onde a própria skill manda seguir o projeto (nome de pacote, prefixo de tabela por módulo, contrato de API já publicado).
- **Não invente o que não verificou.** Método de Util, código da biblioteca de mensagens técnicas, constante de rota, nome de tabela ou coluna, propriedade do Spring Boot: procure no projeto (ou na documentação da versão em uso) antes de usar. Se não existir, diga que não existe e proponha criar, em vez de chamar algo que não compila.
- **Desvio é explícito.** Se uma regra não se encaixa no caso, não a ignore em silêncio: aplique o que fizer sentido e diga qual regra deixou de seguir e por quê.
- **Projeto legado não é refatorado de carona.** Ao tocar num arquivo, corrija o que estiver no seu caminho (injeção por campo da classe que você alterou, `Date` do campo que você mexeu); migração de stack inteira (Boot 2.7 → 4, JUnit 4 → 5) é tarefa própria, combinada com o time.

### Quando perguntar em vez de supor

Pergunte ao usuário antes de escrever quando a resposta mudar o código e não estiver no pedido, no projeto ou nesta skill. Os casos que mais aparecem:

- **Tenant:** a tabela é global ou por empresa (`cd_empresa`)? Por PDV (`cd_pdv`)? De onde vem o `codigoEmpresa` desse fluxo?
- **Idempotência:** a operação pode ser repetida com segurança? Isso decide `@Retryable`, reprocessamento e o desenho do consumidor.
- **Contrato com terceiro ou com o PDV:** formato, timeout aceito, se o parceiro suporta reenvio (idempotency key). Endpoint chamado pela frente de caixa instalada no cliente não muda sem versão nova do PDV.
- **Regra de negócio ambígua:** arredondamento, o que fazer com o caso de borda, qual status devolver.
- **Decisão que é do time, não do código:** adotar serviço gerenciado de nuvem, mudar contrato de API já publicado, subir versão de Boot/Java de um projeto inteiro.

Faça perguntas objetivas e agrupadas — uma lista curta de uma vez, com a opção que você recomenda em cada uma —, não uma por mensagem. Quando a dúvida não bloqueia, siga o padrão mais seguro e registre a suposição na resposta.

### Antes de entregar

Releia o que escreveu contra a skill, como se fosse revisar o MR de outra pessoa: camada e nome certos, injeção por construtor com `final`, `this.` em todo membro, tenant filtrado, nada de `null` atravessando camada, Util no lugar de operador e de `equals`, log sem dado pessoal, `@Transactional` do Spring só no método do `ServiceTransactional` e sem I/O externo dentro, teste para o que tem regra. O que não der para verificar (não compilou, não rodou o teste), diga que não verificou.

## Quatro exigências que valem para todo código

1. **SOLID é obrigatório.** Os cinco princípios são requisito de aceitação. O par interface + `Impl`, a responsabilidade única por camada e o `@Qualifier` no lugar de `if` encadeado já são SOLID aplicado — ver `solid.md`.
2. **Concorrência começa em virtual thread.** Java 21+ e `spring.threads.virtual.enabled=true` são a base; nada de `new Thread`, `CompletableFuture.runAsync` sem executor ou `Executors.newFixedThreadPool` em campo de bean. Ver `threads-and-virtual-threads.md`.
3. **Segurança não é etapa posterior.** Segredo fora do código (nem em constante, nem em `application.properties`), senha com hash forte, endpoint que devolve dado pessoal autenticado, entrada do cliente validada no servidor e **toda query filtrando o tenant**. Ver `security-and-review.md`.
4. **Implemente pensando em cloud native.** Toda implementação responde "como isso se comporta com N réplicas, com memória limitada, podendo ser morto e recriado?" — cache em `HashMap` de Singleton, `@Scheduled` duplicado, pool de conexão, transação longa e segredo em arquivo são os pontos que quebram ao escalar. Quando a limitação for de infraestrutura, **sugira** a alternativa gerenciada de GCP ou AWS. Ver `cloud-native.md`.

## Quando usar

- Iniciando um microsserviço Spring Boot do zero, ou um módulo novo num multi-módulo existente.
- Decidindo em qual camada colocar uma classe nova, ou como nomeá-la.
- Revendo MR quanto a nomenclatura, tipos de dados, estilo, transação ou segurança.
- Escrevendo cliente HTTP, rotina agendada, código concorrente ou cache.
- Escrevendo teste, ou migrando teste de JUnit 4 para JUnit 5.
- Migrando um projeto de Boot 2.7/3.x para Boot 4 (`stack-and-build.md`).
- Avaliando se um trecho aguenta escalar horizontalmente.

## Estrutura de pacotes padrão

Projeto novo é **um módulo só**. Os multi-módulo existentes (`<projeto>-util`, `-repository`, `-service`, `-intercomm`, `-api`) mantêm o desenho; os pacotes dentro de cada módulo são os mesmos abaixo.

```
br.com.empresa.<dominio>
├── api                  (Controller — interface com mapeamento e OpenAPI)
│   └── impl             (ControllerImpl — @RestController, só delega)
├── config               (@Configuration, SecurityFilterChain, @RestControllerAdvice, hints nativos)
├── constants            (RotaConstants, OpenApiTagConstants, <Dominio>Constants)
├── dto
├── enumerator
├── exception            (exceção própria do serviço, quando a da biblioteca não serve)
├── intercomm            (tudo que sai do serviço por HTTP)
│   ├── client           (interface @HttpExchange — sufixo Client)
│   └── service          (IntercommService — a ponta que o Service chama)
│       └── impl
├── job                  (@Scheduled — rotina sem HTTP)
├── mapper               (MapStruct)
├── publisher            (fila/tópico, sem esperar o consumidor)
│   └── impl
├── qualifier            (@Qualifier próprio)
├── repository           (Spring Data — XxxRepository)
│   ├── custom           (XxxRepositoryCustom — contrato da consulta complexa)
│   ├── impl             (XxxRepositoryImpl — QueryDSL / JdbcClient)
│   ├── domain           (entidade JPA e AttributeConverter)
│   │   └── pk           (chave composta)
│   └── projection       (retorno parcial de query, uso interno)
├── service
│   ├── impl
│   ├── facade           (orquestra vários Services — quem o Controller chama)
│   │   └── impl
│   ├── transactional    (dono do @Transactional)
│   │   └── impl
│   ├── application      (cálculo/derivação pura, sem I/O)
│   │   └── impl
│   └── singleton        (estado em memória — raro)
│       └── impl
├── util
├── vo
└── thread               (opcional — mecânica de concorrência reutilizável)
```

**Nome de pacote**: a base tem variações (`enumerator`/`enumerators`, `repository.jpa` para as interfaces Spring Data, `schedule` para agendamento, `controller` no PIX). Nenhuma é errada; o que importa é **seguir o que o projeto já usa** em vez de introduzir a outra no meio. Em projeto novo, use a árvore acima.

**Varredura de componentes**: em projeto de um módulo, a classe `@SpringBootApplication` fica no pacote raiz e a varredura padrão basta. `@ComponentScan`/`@EnableJpaRepositories` com lista de pacotes só existe nos multi-módulo, onde os pacotes vêm de constante (`PKG_*`) — não acrescente varredura explícita em projeto que não precisa.

## Tabela rápida de nomenclatura por camada

Regras completas, exemplos e casos de borda em `layers-and-naming.md`.

| Camada | O que a define | Exemplo |
|---|---|---|
| Controller | interface com `@RequestMapping`, parâmetros e anotações OpenAPI + `Impl` com `@RestController` que só delega. Rota e tag de constante; rota publicada é contrato | `ComandaEletronicaController` |
| Facade | em `service.facade` — orquestra vários Services de domínios diferentes, decide o fluxo, chama integração **fora** da transação. Sem `@Transactional` | `PedidoFacade` |
| Service | `@Service`, onde mora a regra, responsabilidade única | `DocumentoFinanceiroService` |
| ServiceTransactional | em `service.transactional` — a **única** camada com `@Transactional(rollbackFor = Exception.class)` do Spring, sempre no método | `IntegracaoPedidoServiceTransactional` |
| ServiceApplication | em `service.application` — cálculo/derivação pura, sem injeção de I/O; não persiste | `CalculoPontuacaoServiceApplication` |
| ServiceSingleton | em `service.singleton` — estado em memória entre requisições, thread-safe; raro | `ConfiguracaoGeralServiceSingleton` |
| Repository | interface Spring Data, um por entidade; sem `@Repository` | `ComandaEletronicaRepository` |
| RepositoryCustom / RepositoryImpl | consulta dinâmica ou complexa (QueryDSL, `JdbcClient`); a `Impl` leva o nome do Repository + `Impl` | `ComandaEletronicaRepositoryImpl` |
| Entidade | `repository.domain`, espelho da tabela; chave composta em `repository.domain.pk` com sufixo `PK`. Nunca trafega na API | `Destinatario` / `AdministradoraPK` |
| Client | interface `@HttpExchange` em `intercomm.client`, sufixo **`Client`**, sem URL fixa | `CobrancaGatewayClient` |
| IntercommService | em `intercomm.service` — a ponta da cadeia para chamada externa síncrona; trata erro, timeout e resiliência do Client | `CobrancaGatewayIntercommService` |
| Publisher | dispara **assíncrono** (fila/tópico); payload com sufixo `Event` | `PedidoAprovadoPublisher` |
| Job | `@Scheduled` — ocupa o papel do Controller numa rotina sem HTTP | `FechamentoMensalJob` |
| DTO | contrato de API, **singular**; `record` em código novo | `IntegrarComandaEletronicaDTO` |
| Projection | retorno parcial de query, uso interno repository → service | `CabecalhoProjection` |
| VO | domínio interno **com** regra de negócio; não trafega na API | `DocumentoFinanceiroVO` |
| Mapper | MapStruct `componentModel = "spring"`; preferir acima de 3 atributos | `PagamentoCartaoPOSMapper` |
| Enum | atributo `codigo`, `@Getter`, `getByCodigo(...)` | `SituacaoComandaEletronicaEnum` |
| Constants | classe `final` + construtor privado + `public static final`; único caminho para valor usado em anotação | `IntegracaoConstants` |
| Qualifier | `@interface` com `@Qualifier` para desambiguar implementações da mesma interface | `@PagamentoCartaoPOS` |
| Config | sufixo `Config` em projeto novo (`Configuration` nos legados — siga o projeto) | `HttpClientConfig` |

Toda camada é par **interface + `Impl`**, exceto `Job`, `Client`, entidade, `DTO`/`Projection`/`VO`, `Enum`, `Constants`, `Config` e o `Callable` do executor.

## Idioma: regra de ouro

**Domínio de negócio em português, esqueleto técnico em inglês.**

- Português: métodos de negócio (`integrar`, `adquirir`, `salvar`, `lancar`, `efetuarBaixa`, `calcularJuros`), campos (`codigoEmpresa`, `codigoPDV`, `valorPrestacao`, `dataVencimento`), nomes de tabela/coluna, mensagens de log, comentários, mensagens de erro ao usuário.
- Inglês: sufixos de camada e padrões de projeto (`Controller`, `Facade`, `Service`, `Repository`, `Impl`, `DTO`, `VO`), idioms de Java/Lombok/Spring (`getX`/`setX`, `builder()`, `equals()`), anotações.
- **Exceção imposta pelo framework:** método derivado do Spring Data é em inglês porque o nome **é** a query (`findFirstByCodigoEmpresaAndNumeroComandaAndDataExclusaoIsNull`). Método que **você** implementa — no `RepositoryCustom`, com `@Query` ou default — é de negócio e vai em português (`adquirirPorNumeroComanda`).
- Nunca traduza verbo de negócio para inglês fora disso (`find`/`save`/`list` em Service é divergência — use `adquirir`/`salvar`/`listar`).

## Tipos de dados (obrigatório)

| Caso | Tipo |
|---|---|
| Qualquer ID, código, sequencial | `Long` (nunca `int`/`Integer`) |
| CPF e CNPJ | **`String`, sempre — nunca `Long`.** São alfanuméricos (CNPJ alfanumérico) e têm zero à esquerda, que `Long` destrói em silêncio. Coluna `varchar`, gravada sem máscara e em maiúsculo. Na consulta, igualdade direta com a coluna e parâmetro normalizado pelo Util de documento — nunca `REGEXP_REPLACE`/`UPPER` sobre a coluna (ver `database.md`) |
| Valor monetário ou quantidade fracionária | `BigDecimal` (nunca `double`/`float`) |
| Booleano | `Boolean` (wrapper), nunca `boolean` primitivo — exceto onde o framework exige primitivo (atributo de anotação) |
| Data e data-hora, em **qualquer** camada | `LocalDate`/`LocalDateTime` (instante real: `Instant`/`OffsetDateTime`) — **nunca `String`, nunca `java.util.Date`/`@Temporal`**. Sempre com fuso explícito de Brasília (`America/Sao_Paulo`), nunca `now()` sem zona. Na API, data-hora no formato **`dd/MM/yyyy HH:mm:ss`**; só data, `dd/MM/yyyy`. Ver "Data, hora e fuso" em `code-style.md` |
| Situação/tipo de baixa cardinalidade | Enum — nunca `String` comparada com `.equals` solto no código de negócio |
| Coleções | declare pela interface, genérico inferido no `new`: `List<X> listaX = new ArrayList<>();` |

## Números mágicos e constantes

Nenhum número solto no código de negócio. A base já usa constante nomeada por extenso em todo lugar — inclusive em teste —, e é isso que se mantém:

```java
private static final Long TENTATIVAS_LOGIN_LIMITE = 3L;

if (LongUtil.igual(quantidadeTentativas, TENTATIVAS_LOGIN_LIMITE)) { ... }
```

Use os Utils da biblioteca compartilhada com constantes nomeadas (`LongUtil.ZERO`, `.UM`, ..., `.NOVENTA`, `.CEM`; `BigDecimalUtil.ZERO`, `.DEZ`, `.CEM`; `IntegerUtil.QUATRO`; `StringUtil.STRING_VAZIA`) e comparação null-safe (`LongUtil.igual(a, b)`, `LongUtil.isNullOrZero(x)`, `BigDecimalUtil.igual(a, b)`, `ObjectUtil.isNotNull(x)`) — tabela completa em "Comparação e checagem de nulo", `code-style.md`. **Cálculo também é pelo Util** (`BigDecimalUtil.multiplica(a, b)`, `BigDecimalUtil.divide(a, b, escala)`), nunca operador solto — ver "Cálculo" em `code-style.md`. Literal `Long` legítimo (declaração de constante, dado de teste) leva sufixo `L`.

## Referências detalhadas

- **layers-and-naming.md** — cada camada com exemplo de código completo, fluxo de injeção, injeção por construtor, `@Qualifier` com Lombok, exceções e `@RestControllerAdvice`, Client `@HttpExchange`, Publisher, Singleton.
- **code-style.md** — linhas em branco, `public abstract`, `this.` em todo membro, ordem dos membros, constantes, nomes, comentários, Javadoc, logs com `@Slf4j`, datas e fuso, reuso via Util, comparação e cálculo, `Boolean`, lambdas, stream, `Optional`, records, transações, configuração por ambiente, encoding.
- **database.md** — Spring Data × QueryDSL × `JdbcClient` × `@Query` nativa, SQL em caixa alta, nomenclatura de tabela/coluna/sequence/índice, entidade, enum na coluna, Liquibase.
- **testing.md** — JUnit 5 + Mockito strict, `@WebMvcTest` com `MockMvcTester`, `@DataJpaTest` com Testcontainers e `@ServiceConnection`, `@MockitoBean`, nomenclatura, migração de JUnit 4.
- **stack-and-build.md** — versões, starters do Boot 4, `pom.xml`, Jackson 3, springdoc, Actuator/observabilidade, build nativo (AOT, `RuntimeHints`), roteiro de migração 2.7/3.x → 4.
- **solid.md** — os cinco princípios como critério de revisão, ancorados nos padrões desta base.
- **threads-and-virtual-threads.md** — virtual threads no Spring Boot, `@Async`, `@Scheduled`, `@ConcurrencyLimit`, pinning, `ThreadLocal`/MDC/`SecurityContext`, transação entre threads.
- **security-and-review.md** — segredo, senha, Spring Security (`SecurityFilterChain`, resource server JWT), dado pessoal, isolamento entre tenants, CORS, exaustão de recurso, checklist de revisão Java e método de auditoria.
- **cloud-native.md** — as cinco perguntas de escala, ShedLock, Actuator, shutdown gracioso e quando sugerir serviço gerenciado GCP/AWS.

## Divergências conhecidas — não copiar

Padrões que existem na base e **não** devem ser replicados. Corrija o que estiver no seu caminho ao tocar no arquivo; não abra refatoração em massa.

**Stack desatualizada:**

- **Spring Boot 2.7 com `javax.*`** (documento eletrônico, periféricos — este ainda em Java 11). Código novo é `jakarta.*`; a migração do projeto segue `stack-and-build.md`.
- **Swagger 2 / springfox** (`@Api`, `@ApiOperation`, `@ApiParam`, `@ApiModel`) — o padrão é springdoc-openapi com `io.swagger.v3.oas.annotations` (`@Tag`, `@Operation`, `@Parameter`, `@Schema`).
- **OpenFeign com anotações JAX-RS e `Feign.builder()` montado à mão**, com `Logger.Level.FULL` (loga header de autenticação) e `ObjectMapper` novo a cada erro — o padrão é Client `@HttpExchange` sobre `RestClient` (ver `layers-and-naming.md`).
- **ModelMapper** — reflection em runtime, erro só em execução, ruim para build nativo. O padrão é MapStruct.
- **JUnit 4, PowerMock, `@MockBean`, `surefire-junit47`, `cobertura-maven-plugin`** — JUnit 5, Mockito, `@MockitoBean`, JaCoCo (ver `testing.md`).
- **`spring.config.use-legacy-processing`, `spring.profiles.active` dentro do `application.properties`, um arquivo de propriedades completo por sistema operacional** — ver "Configuração por ambiente" em `code-style.md`.
- **`spring-aot-maven-plugin`/Spring Native experimental, `-H:ReflectionConfigurationFiles` à mão** — o caminho é o AOT do Boot com `RuntimeHintsRegistrar` (ver `stack-and-build.md`).

**Contradizem uma regra desta skill:**

- **`@Autowired` em campo e `@Lazy` para quebrar ciclo de dependência** — injeção por construtor com campo `final`; ciclo é erro de desenho e deve falhar na subida (ver `layers-and-naming.md`).
- **`jakarta.transaction.Transactional(rollbackOn = ...)`** misturado com o do Spring — use só `org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)`. `@Transactional` na **classe** inteira, em `RepositoryImpl` ou com chamada HTTP dentro — ver "Transações" em `code-style.md`.
- **`hibernate.enable_lazy_load_no_trans=true`** — esconde N+1 e abre conexão por associação lida fora da transação. Desligue e carregue o que precisa na consulta.
- **`java.util.Date` + `@Temporal`** — `LocalDate`/`LocalDateTime`.
- **`@Data` em entidade com relacionamento**, em DTO e em VO — ver Entidade e DTO em `layers-and-naming.md`.
- **Singleton com `HashMap` e check-then-put** (`containsKey` → `put`) — corrida entre requisições; ver ServiceSingleton em `layers-and-naming.md`.
- **`new Thread(...)`, `CompletableFuture.runAsync` sem executor, `Executors.newFixedThreadPool` em campo de bean sem shutdown, `SimpleTimeLimiter` por bean** — ver `threads-and-virtual-threads.md`.
- **Todo erro de negócio respondendo 400 (legado) ou 503 (PIX)** — o status sai do tipo do erro (ver a tabela em `layers-and-naming.md`), respeitando o contrato já publicado.
- **Validação manual de `null` espalhada pelo Service, sem `@Valid`** — Bean Validation no DTO de entrada.
- **`GET` alterando estado** (`atualizarLimite`, `solicitarDevolucao`) — escrita é `POST`/`DELETE`. Rota publicada não muda sem combinar; rota nova já nasce certa.
- **`@Controller` + `ResponseEntity`** — `@RestController`.
- **Constante de rota numa classe única de 2 mil linhas** — `RotaConstants` com uma classe interna por controller (ver Controller em `layers-and-naming.md`).
- **`Repository` anotado com `@Repository` e estendendo `PagingAndSortingRepository`/`JpaRepository` sem necessidade** — ver Repository em `layers-and-naming.md`.
- **`QuerydslRepositorySupport` com `setEntityManager` sobrescrito e classe de domínio errada no `super(...)`** — injete `JPAQueryFactory` (ver `database.md`).
- **PK chamada só `id`** (PIX) — a convenção é `id_<tabela>`. (A sequence `gen_<tabela>` **não** é divergência: é o padrão dos schemas Spring e se mantém.)
- **Log com concatenação de `String`, `log.info` para erro, `log.error(e.getMessage())`** — placeholders `{}` e a exceção como último argumento.
- **Classe de constantes sem construtor privado** (`ApiConstants`, `S1ServerPDVVendaConstant`).
- **Serviço `Fake` com `@Profile("test")` no código de produção** — o dublê de teste mora em `src/test` (`@MockitoBean`, `@TestConfiguration`), não no jar que vai para produção. Projeto que já tem os fakes mantém; não crie novos.

**Segurança — corrigir ao tocar, e avisar o time:**

- Segredo em código ou em arquivo versionado: segredo HMAC de JWT em literal, usuário/senha do HTTP Basic em constante, `{noop}` em `SecurityConfig`, senha de banco e de keystore em `application*.properties`, certificado `.p12`/`.jks` em `src/main/resources`, senha de deploy no `pom.xml`. Segredo que já foi commitado está comprometido — rotacione, não basta apagar a linha.
- `PasswordEncoder` que compara texto puro, e senha de operador gravada e consultada em texto puro (`findBy...AndSenha...`).
- Filtro JWT que só valida algumas URIs ou pula a validação quando vê `"Basic "`; token sem papéis; token de um ano.
- Token OAuth de parceiro em `log.info`.
- CORS com `applyPermitDefaultValues()` (todas as origens) em serviço exposto.

**Faltam, e deveriam existir:**

- **Bean Validation** nos DTOs de entrada (zero `@Valid` na API de venda).
- **`@ConfigurationProperties`** — a base só usa `@Value` e classes de propriedade preenchidas à mão.
- **Actuator** (health de liveness/readiness, métricas) — está comentado ou substituído por `HealthController` próprio.
- **Virtual threads** — nem o projeto em Java 21 liga `spring.threads.virtual.enabled`.
- **Teste de verdade nos projetos novos** — o PIX só tem `contextLoads()`.
