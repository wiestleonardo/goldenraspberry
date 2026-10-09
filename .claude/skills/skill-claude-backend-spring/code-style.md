# Estilo de código

## Linhas em branco

Use linhas em branco para separar blocos lógicos — cada etapa de um fluxo fica visualmente isolada.

```java
public void processarPedido(Pedido pedido) {

    this.validarPedido(pedido);

    this.calcularTotal(pedido);
    this.aplicarDescontos(pedido);

    this.pedidoServiceTransactional.salvar(pedido);
}
```

Convenção da equipe (já seguida na maior parte da base):

- A **primeira linha** de qualquer bloco (método, `for`, `if`, `else`, `catch`, `case`) fica em branco.
- O **final** do método não tem linha em branco antes do fechamento — exceto em interface, DTO, entidade e `record`.
- Campos injetados separados por linha em branco, um por bloco.

## `public abstract` explícito em método de interface

Todo método declarado numa interface leva `public abstract`, mesmo sendo redundante para o compilador — inclusive nas interfaces do Spring Data e nos `Client` `@HttpExchange`. A assinatura fica visualmente idêntica à da `Impl`, e a convenção é seguida na base inteira. A exceção é a interface de `Mapper` do MapStruct, cuja implementação é gerada.

Método `default` em interface é raro e, quando existe, leva `public default`.

## `this.` — em todo acesso a membro da própria classe

**Sempre** prefixe `this.` ao acessar campo (inclusive dependência injetada) ou método de instância da própria classe. É o padrão dominante da base — centenas de usos por Service — e o que a revisão cobra.

```java
// CERTO
public Long integrar(Long codigoEmpresa, PedidoDTO pedidoDTO) {

    ClienteVO clienteVO = this.clienteService.adquirirAtivo(codigoEmpresa, pedidoDTO.codigoCliente());

    this.validarLimite(clienteVO, pedidoDTO);

    return this.pedidoServiceTransactional.gravar(codigoEmpresa, clienteVO, pedidoDTO);
}

// ERRADO — campo injetado e método privado sem this.
ClienteVO clienteVO = clienteService.adquirirAtivo(codigoEmpresa, pedidoDTO.codigoCliente());
validarLimite(clienteVO, pedidoDTO);
```

O que **não** leva `this.`:

- variável local e parâmetro;
- constante (`static final`) e método estático — da própria classe ou de outra: `TENTATIVAS_LOGIN_LIMITE`, `LongUtil.igual(...)`, `getBusinessServiceException(...)` (import estático).

Dentro de um `record` com método próprio, o componente é acessado como campo: `this.valorTotal`.

Em method reference, `this::adquirirConfiguracao` para método de instância e `NomeDaClasse::metodoEstatico` para estático.

Cuidado relacionado: **chamar um método `@Transactional`, `@Retryable`, `@Cacheable` ou `@Async` da mesma classe não passa pelo proxy do Spring**, e a anotação é ignorada em silêncio — o `this.` não muda isso. Por isso o método transacional mora em outra classe (ver `ServiceTransactional` em `layers-and-naming.md`), e o mesmo vale para retry, cache e assíncrono.

## Ordem dos membros da classe

De cima para baixo: constantes (`private static final`), campos injetados (`private final`), demais campos, construtor (quando escrito à mão), **métodos públicos** e, por último, **métodos privados**.

O leitor abre a classe e vê primeiro o contrato; o detalhe fica embaixo, na ordem em que os públicos o chamam.

## Classe de constantes — `final` e construtor privado

```java
public final class IntegracaoConstants {

    private IntegracaoConstants() {

    }

    public static final Long MODALIDADE_PADRAO = LongUtil.ZERO;
    public static final String FLAG_TIPO_CARTAO_DEBITO = "D";

}
```

- `final` impede que alguém estenda para "herdar" constantes; o construtor privado deixa claro que nunca é instanciada.
- **Uma classe de constantes por assunto** (`RotaConstants`, `HeaderConstants`, `OpenApiTagConstants`, `AgendamentoConstants`, `ResilienciaConstants`, `<Dominio>Constants`) — não uma classe de 2 mil linhas com tudo do serviço. Num projeto que já tem a classe única, acrescente nela; não comece a dividir no meio de uma tarefa.
- Constante usada por uma classe só fica como `private static final` no topo dela.
- Valor usado em **anotação** (`@Scheduled(zone = ...)`, `@Retryable(maxRetries = ...)`, `@PreAuthorize`, `@Tag`) tem de ser constante de tempo de compilação — por isso vem da classe de constantes, nunca de `enum`. Se o nome da classe disser `Enum` e ela não for `enum`, renomeie para `Constants`.
- **String literal repetida três vezes ou mais vira constante** (regra do Sonar que a base já cobra).

## Nomenclatura geral

- `camelCase` para variável e método, `PascalCase` para classe, `UPPER_SNAKE_CASE` para constante.
- Não abrevie (`info`, `prod`, `cli`, `num`, `cod`, `emp`, `str`).
- **Interface sem prefixo `I`.** `PedidoItemService` + `PedidoItemServiceImpl`.
- **Variável, campo e parâmetro de tipo de domínio levam o nome do tipo por extenso, em camelCase** — `DocumentoFinanceiroRepository documentoFinanceiroRepository`, `ProdutoDTO produtoDTO`; nunca `repo`, `service`, `dto`, `obj`, `p`. Duas variáveis do mesmo tipo no mesmo escopo levam o papel no nome (`produtoDTOOrigem`, `produtoDTODestino`). Isso vale para exceção capturada também (`HttpClientErrorException httpClientErrorException`), salvo quando o `catch` agrupa tipos (`excecaoIndisponibilidade`).
- Singular e plural do mesmo atributo no mesmo método: prefixo `lista` na plural (`codigoProduto`, `listaCodigoProduto`).
- Sufixo de módulo no nome da entidade (`OperadorVEN`, `AdministradoraPDV`) segue o que o projeto já faz: ele espelha o sufixo da tabela.

## Comentários

Comente o **porquê**, nunca o **como**.

```java
// RUIM — repete o código
// Busca o cliente pelo id
ClienteVO clienteVO = this.clienteService.adquirir(codigoEmpresa, codigoCliente);

// BOM — conta o que o código não tem como mostrar
// SEFAZ rejeita a NFC-e com mais de 990 itens; o lote é quebrado antes do envio
private static final Long LIMITE_ITENS_NFCE = 990L;
```

- Antes de comentar, tente resolver pelo nome: extraia método ou renomeie até o nome dizer o que o trecho faz.
- Código comentado morto não fica no repositório — o git já guarda.
- `TODO`/`FIXME` só com o número da tarefa (`// TODO #4821 - remover após a migração do PDV`).

## Javadoc

- Na **interface**, não na `Impl`.
- **Javadoc que não acrescenta nada não se escreve.** Só o que o leitor não deduz pelo nome: regra escondida, exceção lançada e quando, efeito colateral, o porquê de uma escolha.
- DTO, Projection e VO sem regra não têm Javadoc de classe; o que precisa ser explicado do DTO vai no `@Schema(description = ...)`.

## Logs

**Sempre Lombok `@Slf4j`** (SLF4J sobre Logback) — nunca `LoggerFactory.getLogger(...)` à mão, `System.out`/`System.err`, `e.printStackTrace()` nem `java.util.logging`, em nenhuma camada, nem para debug temporário.

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AutenticacaoOperadorServiceImpl implements AutenticacaoOperadorService {

    private final OperadorRepository operadorRepository;

    private final PasswordEncoder passwordEncoder;

    private final OperadorMapper operadorMapper;

    @Override
    public OperadorVO autenticar(Long codigoEmpresa, String login, String senha) {

        Optional<Operador> operador = this.operadorRepository.findFirstByCodigoEmpresaAndLoginIgnoreCaseAndDataExclusaoIsNull(codigoEmpresa, login);

        if (operador.isEmpty() || !this.passwordEncoder.matches(senha, operador.get().getHashSenha())) {

            log.warn("Tentativa de autenticação recusada na empresa {}", codigoEmpresa);
            throw getBusinessServiceException(CREDENCIAL_INVALIDA);
        }

        log.info("Operador {} autenticado na empresa {}", operador.get().getIdOperador(), codigoEmpresa);

        return this.operadorMapper.map(operador.get());
    }

}
```

O exemplo não loga o login (dado pessoal) — loga o identificador; não diferencia "não existe" de "senha errada"; e não envolve o fluxo num `try/catch (Exception)` só para logar. A exceção sobe até o handler global, que loga uma vez.

- **Placeholder `{}`, sempre** — nunca concatenação (`"Pedido " + idPedido`) nem `String.format` dentro do log. O placeholder só monta a mensagem se o nível estiver ligado.
- **Exceção é o último argumento**, sem placeholder para ela: `log.error("Falha ao consultar a comanda {}", numeroComanda, exception)`. `log.error(e.getMessage())` descarta o stack trace.
- `debug` detalhe técnico · `info` evento normal · `warn` inesperado mas não fatal · `error` falha, sempre com o `Throwable`. Erro não se loga em `info`.
- **Dentro de laço, `debug`, não `info`.** Um `info` por item de um lote de 10 mil enterra o log de produção; o resumo sai em `info` depois do laço.
- **Onde logar:** a entrada no Controller (ver `layers-and-naming.md`); no Service e na Facade, só mudança de fluxo relevante (desvio de regra, caminho não óbvio) e o dado carregado que ajuda a entender uma ocorrência — pelo identificador.
- **Toda rotina crítica de negócio deixa rastro** — fluxo que decide algo de dinheiro, autenticação, segurança ou integração.
- **Logar e relançar** a mesma exceção gera o stack trace duas vezes. Ou trata, ou deixa subir.
- **Prefixo**: se o projeto tem as frases padronizadas (`EXECUTANDO_METODO`, `"==> "`), siga-as; em projeto novo, a frase vai direto, com maiúscula, sem prefixo nem colchete — a categoria do log já diz a classe.
- **Dado sensível nunca**: credencial (senha, token, header `Authorization`, certificado) nem dado pessoal (CPF/CNPJ, e-mail, telefone, nome; lista em `security-and-review.md`). Isso inclui o DTO de entrada inteiro, o DTO de token de parceiro e o corpo de requisição HTTP.
- **Correlação entre linhas** vem do tracing do Micrometer (`traceId`/`spanId` no MDC, ver `stack-and-build.md`), não de filtro próprio de MDC em projeto novo.

## Texto fixo (mensagens)

Mensagem ao usuário nunca é literal no código de negócio: vem do arquivo de mensagens técnicas (pelo código, via `getBusinessServiceException`) ou, em serviço standalone, do `messages.properties` via `MessageSource`. Valor fixo (número mágico) é assunto do `SKILL.md`.

## Data, hora e fuso — pt-BR e Brasília, sempre explícitos

O ambiente de execução **não** é Brasília: container roda em UTC. Nenhuma data pode depender do relógio ou do idioma do host.

**A hora atual vem de um `Clock` injetado**, criado uma vez no fuso de Brasília:

```java
@Configuration(proxyBeanMethods = false)
public class RelogioConfig {

    @Bean
    public Clock clock() {

        return Clock.system(DateUtil.FUSO_BRASILIA);
    }

}
```

```java
LocalDateTime dataCadastro = LocalDateTime.now(this.clock);
```

- `LocalDate.now()` / `LocalDateTime.now()` sem argumento é **bug latente**: depois das 21h de Brasília o UTC já virou o dia seguinte, e o fechamento diário, o vencimento ou o `dt_cadastro` cai na data errada — só à noite, o que torna o bug difícil de reproduzir de manhã.
- O `Clock` injetado também é o que torna a regra testável: no teste, `Clock.fixed(...)` congela o horário — inclusive o das 23h30 que vira o dia seguinte em UTC (ver "D" em `solid.md`).
- `DateUtil.getDateTimeNow()` e similares da biblioteca compartilhada: confira se usam fuso explícito antes de confiar. Se usarem `ZoneId.systemDefault()`, a correção é no próprio util (sobrecarga com fuso), não um `ZoneId.of(...)` solto no Service.
- `America/Sao_Paulo`, nunca deslocamento fixo (`-03:00`).
- `DateTimeFormatter` e `String.format` usam o `Locale` do host quando não se passa um — passe `Locale.of("pt", "BR")`.

### Data nunca é `String`, nunca `java.util.Date`

Data trafega como `LocalDate` (só dia) ou `LocalDateTime` (dia e hora de parede) em toda camada: entidade, Projection, VO, DTO de entrada e de saída. Instante real (auditoria, integração entre sistemas, ordenação entre fusos) é `Instant`/`OffsetDateTime`.

- `String` com data dentro perde ordenação, validação e fuso. Não formate data no SQL (`TO_CHAR`).
- `java.util.Date` + `@Temporal` é a API antiga (mais de mil ocorrências na base): mutável, sem fuso explícito, com conversão implícita pelo fuso do host. Campo novo é `java.time`; ao mexer num campo `Date`, migre-o.
- `@CreationTimestamp`/`@UpdateTimestamp` do Hibernate em `LocalDateTime` usam o relógio da JVM — em container, UTC. Preencha a data no Service com o `Clock`.

**Formato da API: `dd/MM/yyyy HH:mm:ss`** (horário de Brasília) para data-hora, `dd/MM/yyyy` para data — na serialização do DTO, com a máscara nomeada do `DateUtil`:

```java
@JsonFormat(pattern = DateUtil.FORMATO_DD_MM_YYYY_HH_MM_SS)
@Schema(type = "string", example = "23/09/2026 17:19:04", description = "Data e hora de cadastro, horário de Brasília")
LocalDateTime dataCadastro
```

O mesmo `@JsonFormat` vale para entrada. Contrato já publicado em ISO (`2026-09-23T17:19:04`) não muda sem combinar com o consumidor. Teste conversão de data com 23h30 e rode a suíte em UTC (`testing.md`).

## Reuso — nada de função isolada, sempre um Util

**Antes de escrever qualquer método auxiliar, procure no Util.** A biblioteca de utilitários compartilhada (dependência no `pom.xml`, em geral com `util` no nome) cobre data, string, número, lista, objeto, documento, JSON — e a resposta certa quase sempre é usar o que existe.

1. **Já existe no Util?** Use.
2. **Existe o Util do assunto, mas falta o método?** Acrescente **lá**, não na sua classe. É biblioteca compartilhada: prefira sobrecarga a mudar assinatura existente.
3. **Não existe Util para o assunto?** Crie um `XxxUtil` — `final`, construtor privado, métodos estáticos, sem estado. Nunca `DateUtil2`.

Os nomes que existem na base: `ObjectUtil`, `LongUtil`, `IntegerUtil`, `BigDecimalUtil`, `StringUtil`, `ListUtil`, `BooleanUtil`, `DateUtil`, `RegexUtil` (com as expressões em `RegexConstants` — regex literal inline no código de negócio não). Confirme o método na versão em uso antes de chamá-lo.

**Serviço standalone sem a biblioteca** (como o PIX) não reimplementa um subconjunto dela em `util` — ou adiciona a dependência, ou, se não puder, cria os Utils com os **mesmos nomes e assinaturas**, para o código se ler igual nos dois mundos.

Método privado de formatação, validação ou comparação dentro de um Service é sinal de que a etapa 1 foi pulada.

## Comparação e checagem de nulo — pelo Util

Em `if`, filtro e comparação de negócio, use o Util do tipo no lugar de `==`, `!=`, `.equals()` e `== null`. O operador sobre wrapper faz unboxing de nulo (NPE) ou compara referência (`Long` acima de 127 com `==` dá `false`); `a.equals(b)` estoura com `a` nulo; `BigDecimal.equals` considera a escala (`1.0` ≠ `1.00`).

| Evite | Prefira |
|---|---|
| `a.equals(b)`, `a == b` (`Long`) | `LongUtil.igual(a, b)` |
| `!a.equals(b)`, `a != b` (`Long`) | `LongUtil.diferente(a, b)` |
| `x == null` / `x != null` | `ObjectUtil.isNull(x)` / `ObjectUtil.isNotNull(x)` (para `Long`, também `LongUtil.isNull`/`isNotNull`) |
| `x == null \|\| x == 0` | `LongUtil.isNullOrZero(x)` |
| `Objects.equals(o1, o2)` | `ObjectUtil.isEquals(o1, o2)` |
| `s == null \|\| s.isEmpty()` / `!StringUtil.isNullOrEmpty(s)` | `StringUtil.isNullOrEmpty(s)` / `StringUtil.notEmpty(s)` |
| `lista == null \|\| lista.isEmpty()` / `!ListUtil.isNullOrEmpty(lista)` | `ListUtil.isNullOrEmpty(lista)` / `ListUtil.notEmpty(lista)` |
| `v1.compareTo(v2) == 0` (`BigDecimal`) | `BigDecimalUtil.igual(v1, v2)` |
| `v.compareTo(BigDecimal.ZERO) > 0` | `BigDecimalUtil.maiorQueZero(v)` |
| `Boolean.TRUE.equals(b)` | `BooleanUtil.isTrue(b)` / `BooleanUtil.isFalse(b)` / `BooleanUtil.isNullOrFalse(b)` |

- **Prefira o método afirmativo** (`notEmpty`, `isNotNull`, `diferente`) a negar o outro com `!`.
- **Enum se compara com `==`.**
- **Ordem (`<`, `>`) sobre wrapper também faz unboxing.** Use o método de ordem do Util se existir; se não, acrescente-o lá ou garanta o não nulo com guard clause antes.
- `Boolean` controlado internamente, que nunca é nulo, continua no operador direto (ver `Boolean` abaixo).

## Cálculo — sempre pelo Util

Soma, subtração, multiplicação, divisão e percentual de código de negócio passam pelo Util do tipo (`BigDecimalUtil.soma`, `.menos`, `.multiplica`, `.divide(a, b, escala)`, `.ajustarCasasDecimais`; o equivalente de `LongUtil`/`IntegerUtil`). Operador sobre wrapper estoura NPE com nulo, e em `Long`/`Integer` o estouro de faixa passa em silêncio. Em `BigDecimal`, o Util fixa escala e arredondamento — `BigDecimal.divide` sem escala lança `ArithmeticException` em dízima.

Se a operação não existir no Util, acrescente lá (com teste do nulo e do estouro — `Math.multiplyExact`/`addExact`), não resolva com operador "só desta vez".

## Conversão de tipo — pelo Util

`Long.valueOf`, `Integer.parseInt` e `new BigDecimal(texto)` sobre entrada externa lançam exceção na primeira entrada inesperada. Use o conversor do Util (`LongUtil.toLong`, `BigDecimalUtil.toBigDecimal`...). Literal em teste (`new BigDecimal("100.00")`) não é conversão de entrada e continua valendo.

## Controle de fluxo — early return em vez de `else`

Trate primeiro o caso que encerra o método e retorne; o caminho principal fica por último, sem indentação extra. Se o `if` termina em `return`/`throw`, o `else` não acrescenta nada.

```java
public TipoPessoaEnum identificarTipoPessoa(String flagTipoPessoa, Long codigoOrigemDados) {

    if (StringUtil.isNullOrEmpty(flagTipoPessoa)) {

        return TipoPessoaEnum.NAO_INFORMADO;
    }

    if (LongUtil.igual(codigoOrigemDados, OrigemDadosEnum.EXTERNA.getCodigo())) {

        return this.identificarExterna(flagTipoPessoa);
    }

    return this.identificarInterna(flagTipoPessoa);
}
```

Ternário é aceitável para retorno simples entre dois valores, não para esconder `if` de regra. `switch` sobre enum com expressão (`case X -> ...`) é a forma moderna e exaustiva — o compilador avisa quando falta uma constante.

## Tamanho de métodos

Até 20-30 linhas, uma coisa só. **50 linhas é o limite — passou, quebre.**

- **Método privado** quando o trecho é um passo do mesmo fluxo.
- **Service próprio** quando o trecho é uma responsabilidade nomeável e reusável. Se a única razão é "ficou grande", é método privado.

## `Boolean`

Expressão que já é booleana não vai dentro de ternário (`return a < b ? Boolean.TRUE : Boolean.FALSE;` → `return a < b;`).

- **Valor controlado internamente**, nunca nulo: operador direto (`if (encontrado)`).
- **Valor que pode ser nulo** (campo de entidade, DTO, configuração): `BooleanUtil.isTrue(...)`. `Boolean` nulo em `if` é NPE.

Usar o Util em tudo esconde bug: um `null` que não deveria existir vira `false` em silêncio.

## Lambdas, stream e `Optional`

- Method reference em vez de lambda que só repassa (`ProdutoDTO::descricao`).
- Lambda de uma expressão sem chaves nem `return`.
- **`.toList()`** no lugar de `collect(Collectors.toList())` — a lista é **imutável**; se o chamador vai alterá-la, `Collectors.toCollection(ArrayList::new)`.
- Stream para transformar/filtrar/agrupar. Fluxo sequencial com efeito colateral, `break` ou várias etapas por item fica num `for`.
- **`Optional` é para retorno** em que a ausência é resultado normal — o padrão do Spring Data para busca única. Nunca como parâmetro, campo ou atributo de DTO. Lista nunca vem em `Optional`. Em regra de negócio, `ObjectUtil.isNull(x)` com early return lê melhor que `Optional.ofNullable(x).map(...).orElse(...)`.
- `Optional.get()` só depois de `isPresent()`/`isEmpty()` no mesmo bloco; prefira `orElseThrow(...)`.

## Java 21+ — recurso moderno quando é a forma natural

- **`record`** para DTO, Projection, chave composta de mapa, `@ConfigurationProperties` e evento.
- **Pattern matching** (`if (exception instanceof HttpClientErrorException httpClientErrorException)`, `switch` com padrão de tipo) no lugar de `instanceof` + cast.
- **`switch` com seta** sobre enum.
- **Text block** (`"""`) para SQL fixo de `@Query` e para JSON de teste. SQL montado dinamicamente continua em `StringBuilder` (ver `database.md`).
- **`var`** é raro — o padrão da base é declarar o tipo. Nunca em coleção, que se declara pela interface (`List<PedidoDTO> listaPedidoDTO = new ArrayList<>();`).
- **Sequenced collections** (`getFirst()`, `getLast()`, `reversed()`) no lugar de `lista.get(0)`/`lista.get(lista.size() - 1)`.
- Virtual threads: `threads-and-virtual-threads.md`.

## Anotações Jackson

O Boot 4 usa **Jackson 3**: o pacote do núcleo mudou para `tools.jackson.*` (`JsonMapper`, `ObjectMapper`), mas as **anotações continuam em `com.fasterxml.jackson.annotation`**. Não injete nem crie `ObjectMapper` à mão: use o `JsonMapper` que o Spring configura (customização por `JsonMapperBuilderCustomizer` ou `spring.jackson.*`).

| Anotação | Quando usar |
|---|---|
| `@JsonFormat(pattern = ...)` | data/hora na API, com a máscara do `DateUtil` |
| `@JsonProperty("nome")` | nome no JSON diferente do campo |
| `@JsonNaming(SnakeCaseStrategy.class)` | contrato de parceiro em snake_case — só no DTO do parceiro (`Api*DTO`), nunca no DTO da nossa API |
| `@JsonInclude(NON_NULL)` | omitir nulos — **não** em DTO onde `null` tem significado |
| `@JsonIgnore` | campo que não sai no JSON |
| `@JsonValue` / `@JsonCreator` | enum que trafega pelo código (ver Enum em `layers-and-naming.md`) |

## Configuração por ambiente

**Uma imagem só para todos os ambientes; o que muda entra no deploy.**

- **`.properties`, não YAML** — é o que a base inteira usa. Organize em blocos por assunto, com comentário de seção (`#Database`, `#HTTP Client - gateway`, `#Agendamento`).
- Valor que muda por ambiente vira propriedade com **variável de ambiente**: `spring.datasource.url=${DB_URL}`.
- Diferença fixa entre local, teste e produção: `application-<perfil>.properties` **sobrepondo** o `application.properties` (só as chaves que mudam, nunca a cópia inteira). O perfil ativo vem do deploy (`SPRING_PROFILES_ACTIVE`), **nunca** de `spring.profiles.active` dentro do `application.properties` versionado. Arquivo por sistema operacional (`application-linux.properties` completo) é divergência.
- **Segredo nunca em `.properties`, `Dockerfile`, constante Java ou `pom.xml`** — nem como valor padrão (`${DB_PASSWORD:senha}` é segredo commitado). Vem de variável de ambiente alimentada pelo Secret Manager (ver `security-and-review.md`).
- Propriedade obrigatória sem valor faz a aplicação **falhar na subida** — é o comportamento certo.
- **Configuração com mais de um valor vira `@ConfigurationProperties` em `record`, validado:**

  ```java
  @Validated
  @ConfigurationProperties(prefix = "fechamento")
  public record FechamentoProperties(

          @NotNull
          @Positive
          Integer limiteConcorrencia,

          @NotNull
          Duration intervaloReprocessamento) {

  }
  ```

  Registrado com `@ConfigurationPropertiesScan` na aplicação e injetado como bean. Tipado, validado na subida e documentado pelo `spring-boot-configuration-processor`. `@Value` fica para valor único e isolado; `@Value` com constante de placeholder espalhado por dez classes é divergência.
- Configuração de máquina carregada de caminho fixo (`@PropertySource("file:c:/...")`) existe nos serviços instalados no cliente (PDV, periféricos, agente); em serviço de nuvem, não — lá a configuração vem do ambiente. Para arquivo externo opcional, `spring.config.import=optional:file:./config/`.

## Encoding de arquivos — preserve o original

**Nunca altere o encoding de um arquivo existente.** Fonte legado convertido quebra acentuação, e o diff passa a mostrar o arquivo inteiro.

- Arquivo **novo**: UTF-8 sem BOM.
- `.properties` em ISO-8859-1 **não** se converte sem alinhar com a equipe.
- Changelog Liquibase: só caracteres representáveis em LATIN1 (ver `database.md`).
- Confira o diff antes de commitar.

## Versões no `pom.xml` — sempre por propriedade

Nenhuma `<version>` literal em dependência, plugin ou `annotationProcessorPaths`. A versão fica em `<properties>` e é referenciada com `${...}`; o que o BOM do Spring Boot (ou o BOM da organização) já gerencia não leva `<version>`. Siga o prefixo de propriedade que o projeto já usa. Detalhe em `stack-and-build.md`.

## Transações com banco de dados

Erro sutil aqui vira inconsistência de dados em produção.

- **Só o `ServiceTransactional` tem `@Transactional`**, o do Spring, **no método**, com `rollbackFor = Exception.class` (ver `layers-and-naming.md`).
- **Cubra exatamente a operação atômica** — nem menos (escrita fora da transação), nem mais (transação larga segurando conexão e lock).
- **I/O externo nunca dentro de `@Transactional`** — `IntercommService`, Publisher, envio de e-mail, chamada ao PDV. A transação fica aberta esperando a rede, segurando conexão do pool; e, se a chamada passou e o commit falhou, o parceiro já agiu sobre um dado que não existe. A base tem casos (`integracaoAfericaoService.enviar(...)` dentro do lançamento): ao tocar, mova para a Facade, depois do commit.
- **Falha pós-commit se trata isoladamente** — log, reenvio, pendência; nunca tentar desfazer o que já commitou.
- **Nunca engula exceção** dentro do bloco transacional (`catch (Exception e) {}`) — sem propagação não há rollback. E cuidado com o inverso: exceção capturada e tratada **dentro** de um método chamado pelo transacional pode já ter marcado a transação como *rollback-only*, e o commit lança `UnexpectedRollbackException`.
- **Auto-invocação ignora a anotação** (ver `this.` acima).
- **Não aninhe transações manualmente.** `REQUIRES_NEW` só com motivo escrito.
- **`spring.jpa.open-in-view=false`** sempre, e **`hibernate.enable_lazy_load_no_trans` nunca** — o dado que o fluxo precisa vem na consulta.
- Escrita em massa: `saveAll` com `hibernate.jdbc.batch_size` configurado, ou `UPDATE`/`DELETE` em lote no `RepositoryCustom` — nunca `save` dentro de laço com milhares de itens.
