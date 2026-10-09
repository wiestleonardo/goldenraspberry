# Stack, build e migração

## Versões

| Item | Projeto novo | Mínimo aceito |
|---|---|---|
| Java | **25 (LTS)** | 21 |
| Spring Boot | **4.x, a linha corrente** (4.1 em junho de 2026) | 4.0 |
| Spring Framework | 7.x (vem do Boot) | — |
| Jakarta EE | 11 (`jakarta.*`) | — |
| Hibernate ORM | 7.x (vem do Boot) | — |
| Jackson | 3.x (vem do Boot) | — |
| JUnit | Jupiter (vem do Boot) | — |

- **Por que Java 25 e não 21:** a partir do Java 24 (JEP 491) um `synchronized` não prende mais a virtual thread na thread de plataforma — com virtual threads ligadas, isso elimina a principal armadilha de desempenho em código legado e em biblioteca de terceiro (ver `threads-and-virtual-threads.md`). Java 21 continua aceito; nele, a regra do `ReentrantLock` é obrigatória.
- **Versão de Boot e de Java nunca literal no módulo.** Nos projetos com o parent/BOM da organização, as versões vêm de lá (`${spring.boot.*.version}`, `${java.*.version}`); em projeto standalone, do `spring-boot-starter-parent`. Antes de escrever código que depende de recurso do Boot 4 (`@ImportHttpServices`, `@Retryable` do Framework, `@MockitoBean`), confira **qual Boot o projeto usa** — a base hoje vai do 2.7 ao 3.5.
- Antes de afirmar uma propriedade ou anotação do Spring, confira na documentação da versão em uso: nomes de propriedade mudaram entre 3.x e 4.x (cliente HTTP, Jackson, testes).

## `pom.xml`

**Starters do Boot 4 são por tecnologia.** O que um serviço desta base costuma usar:

| Necessidade | Starter |
|---|---|
| API REST (Spring MVC + Tomcat) | `spring-boot-starter-webmvc` (o antigo `spring-boot-starter-web`) |
| Bean Validation | `spring-boot-starter-validation` |
| JPA + Hibernate | `spring-boot-starter-data-jpa` |
| `JdbcClient` sem JPA | `spring-boot-starter-jdbc` |
| Liquibase | `spring-boot-starter-liquibase` (no Boot 4 a autoconfiguração saiu do núcleo — só a lib `liquibase-core` não basta) |
| Cliente HTTP declarativo (`@HttpExchange` sobre `RestClient`) | `spring-boot-starter-restclient` |
| Segurança | `spring-boot-starter-security`; JWT: o starter de OAuth2 resource server |
| Health, métricas | `spring-boot-starter-actuator` |
| Tracing/OpenTelemetry | `spring-boot-starter-opentelemetry` |
| Cache local | `spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` |
| WebSocket/STOMP (push para o PDV) | `spring-boot-starter-websocket` |
| OpenAPI | `org.springdoc:springdoc-openapi-starter-webmvc-ui`, **linha 3.x** (a compatível com Boot 4) |

Confira o nome exato no catálogo de starters da versão — o Boot 4 renomeou alguns e criou outros.

**Processadores de anotação, versão por propriedade:**

```xml
<properties>
    <java.version>25</java.version>
    <dependency.version.mapstruct>...</dependency.version.mapstruct>
    <dependency.version.lombok-mapstruct-binding>...</dependency.version.lombok-mapstruct-binding>
</properties>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${dependency.version.mapstruct}</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>${dependency.version.lombok-mapstruct-binding}</version>
            </path>
            <path>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-configuration-processor</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

- **`<parameters>true</parameters>`** é obrigatório: sem ele, `@PathVariable`/`@RequestParam` sem nome explícito e parâmetro nomeado de `@Query` falham em runtime (o Spring 6.1+ não lê mais o nome do bytecode de debug). O parent do Boot já liga; projeto com parent próprio precisa declarar.
- **QueryDSL**: `querydsl-apt` com classifier `jakarta` no `annotationProcessorPaths`, mesma versão do `querydsl-jpa`. Confira a compatibilidade com o Hibernate do Boot (ver `database.md`).
- Versão literal só a do próprio projeto. `pom.xml.versionsBackup` não se commita.
- **Credencial de repositório Maven** (Artifactory) vem do `settings.xml` do usuário ou do CI, **nunca** do `pom.xml`.
- `spring-boot-maven-plugin` sem versão — vem do Boot. Plugin do Boot 2.7 num projeto Boot 3 (existe na base) é bug de build.

## Estrutura da aplicação

```java
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableResilientMethods
public class PedidoApplication {

    public static void main(String[] args) {

        SpringApplication.run(PedidoApplication.class, args);
    }

}
```

- A classe principal no **pacote raiz**: a varredura padrão encontra todos os beans, repositórios e entidades. `scanBasePackages`, `@EnableJpaRepositories(basePackages = ...)` e `@EntityScan` só nos multi-módulo cujos pacotes não estão sob a raiz.
- `@Enable*` na classe principal ou numa `@Configuration` por assunto — uma vez só.
- Não exclua autoconfiguração sem motivo escrito (o serviço de venda exclui o Liquibase porque o schema vem de outro artefato — isso é motivo).
- `@Configuration(proxyBeanMethods = false)` em toda configuração cujos `@Bean` não chamam uns aos outros — é o padrão da base e reduz o custo de proxy (e ajuda o AOT).

## Propriedades essenciais

```properties
#Aplicacao
spring.application.name=pedido-api
spring.threads.virtual.enabled=true
spring.main.keep-alive=true

#Database
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.hikari.maximum-pool-size=${DB_POOL_MAXIMO:10}
spring.datasource.hikari.connection-timeout=5s

#JPA/Hibernate
spring.jpa.open-in-view=false
spring.jpa.hibernate.ddl-auto=none
spring.jpa.properties.hibernate.jdbc.batch_size=50
spring.jpa.properties.hibernate.order_inserts=true

#Servidor
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
server.error.include-stacktrace=never
server.error.include-message=never

#Actuator
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true

#Log
logging.structured.format.console=ecs
```

- `spring.main.keep-alive=true` mantém a JVM de pé quando só há virtual threads (que são daemon) — necessário em serviço sem servidor web, só com `@Scheduled`.
- `connection-timeout` curto do Hikari: com virtual threads, o gargalo vira o pool, e é melhor falhar rápido do que empilhar milhares de requisições esperando conexão.
- Log estruturado em JSON no console só no perfil de nuvem; local, o padrão legível. Log em arquivo (`logging.file.name`) é para os serviços instalados no cliente (PDV, periféricos, agente) — em container, stdout.

## Jackson 3

- Pacotes do núcleo em `tools.jackson.*`; anotações continuam em `com.fasterxml.jackson.annotation`.
- `JsonMapper` (o `ObjectMapper` do Jackson 3) é **imutável** e configurado por builder — customize com um bean `JsonMapperBuilderCustomizer`, nunca criando outro mapper. Mapper próprio para um parceiro com convenção diferente (snake_case) se resolve com `@JsonNaming` no DTO do parceiro, não com um segundo mapper global.
- O suporte a `java.time` é nativo no Jackson 3 — sem registrar `JavaTimeModule`.
- Projeto com muito código Jackson 2 pode usar a ponte de compatibilidade do Boot durante a migração; código novo é Jackson 3.

## OpenAPI (springdoc)

- `springdoc-openapi-starter-webmvc-ui` 3.x; anotações `io.swagger.v3.oas.annotations` (`@Tag`, `@Operation`, `@Parameter`, `@ApiResponse`, `@Schema`, `@OpenAPIDefinition`, `@SecurityScheme`).
- Interface de documentação desligada em produção quando a API não é pública (`springdoc.swagger-ui.enabled=false` e `springdoc.api-docs.enabled=false` no perfil de produção).
- Springfox (`@Api`, `@ApiOperation`, `@ApiModel`) não roda no Boot 3+: ao migrar o projeto, troque as anotações. Anotação de OpenAPI em VO de módulo `util` é vazamento de camada — só DTO de contrato leva `@Schema`.

## Observabilidade

- **Actuator** com health de **liveness** (só o processo) e **readiness** (dependências) separados — ver `cloud-native.md`. Não crie `HealthController` próprio.
- **Tracing** com Micrometer/OpenTelemetry: `traceId` e `spanId` entram no MDC sozinhos e se propagam nas chamadas do `RestClient` e nas virtual threads do `@Async`. Isso substitui o filtro próprio de `X-Correlation-Id` em projeto novo; nos existentes, mantenha o header como contrato com quem já o envia.
- **Métrica de negócio** com `MeterRegistry` (contador de cobranças geradas, timer de integração) ou `@Observed` — no lugar de logar tempo com cronômetro manual.

## Build nativo (GraalVM) e AOT

O serviço de venda e o PIX buildam nativo. O caminho atual é o **AOT do Spring Boot**:

- `spring-boot-maven-plugin` com o goal `process-aot` e o `native-maven-plugin` do GraalVM num perfil `native`; ou imagem por buildpack (`spring-boot:build-image` com `BP_NATIVE_IMAGE=true`).
- **Reflection, recurso e proxy por `RuntimeHints`**, não por `reflect-config.json` escrito à mão nem por `-H:` no `buildArgs`:

  ```java
  public class MensagemTecnicaRuntimeHints implements RuntimeHintsRegistrar {

      @Override
      public void registerHints(RuntimeHints runtimeHints, ClassLoader classLoader) {

          runtimeHints.resources().registerPattern("mensagem-tecnica*.properties");
      }

  }
  ```

  registrado com `@ImportRuntimeHints(MensagemTecnicaRuntimeHints.class)` numa `@Configuration`.
- **Classe serializada fora da assinatura de um controller ou Client** (desserializada à mão, guardada em cache, lida de arquivo) leva `@RegisterReflectionForBinding`. DTO de `@RequestBody`/retorno de controller e de `@HttpExchange` o AOT já registra.
- Biblioteca de terceiro: confira o GraalVM Reachability Metadata antes de escrever hint para ela.
- **ModelMapper, PowerMock e reflection por nome de classe** são o que mais quebra no nativo — mais um motivo para MapStruct.
- `@Profile` e `@ConditionalOnProperty` são avaliados **no build** do AOT: configuração que precisa mudar em runtime numa imagem nativa não pode depender de bean condicional. Teste o binário nativo no CI, não só o jar.
- `--initialize-at-build-time` com lista longa de classes é sintoma de hint faltando; não acrescente classe à lista sem entender por quê.

## Roteiro de migração de um projeto existente

Projeto em Boot 2.7 (`javax.*`) ou 3.x indo para Boot 4. É tarefa própria, combinada com o time — não de carona numa feature.

1. **Suba para o último 3.5 primeiro** e zere os avisos de depreciação. Pular direto de 2.7 para 4 mistura três migrações e ninguém sabe qual quebrou o quê.
2. **De 2.7 para 3.x:** `javax.*` → `jakarta.*` (persistence, validation, servlet, annotation, transaction); Java 17+; Hibernate 6 (QueryDSL com classifier `jakarta`); `WebSecurityConfigurerAdapter` → `SecurityFilterChain`; springfox → springdoc.
3. **De 3.5 para 4:** starters renomeados (`webmvc`), Jackson 3, `@MockBean` → `@MockitoBean`, starters de teste por tecnologia, springdoc 3, Liquibase com starter próprio, propriedades renomeadas (o `spring-boot-properties-migrator` em runtime aponta as antigas — remova-o depois).
4. **OpenRewrite** tem receitas de migração para Boot 3 e Boot 4 e para JUnit 4 → 5: rode, revise o diff como de qualquer pessoa, e só então ajuste à mão.
5. **Java 21+** e `spring.threads.virtual.enabled=true` — depois de revisar `synchronized` com I/O (Java 21) e `ThreadLocal` usado como cache (ver `threads-and-virtual-threads.md`).
6. Remova o que só existia por causa da versão antiga: `spring.config.use-legacy-processing`, `spring-aot-maven-plugin` comentado, `cobertura-maven-plugin`, `surefire-junit47` (quando o último teste JUnit 4 sair).
7. **Build nativo** refeito com AOT e `RuntimeHints`.

Cada passo termina com a suíte verde e o serviço subindo no ambiente de homologação.
