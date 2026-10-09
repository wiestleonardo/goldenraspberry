# Camadas, nomenclatura e responsabilidade única

## Fluxo de camadas — direção única de injeção

```
Controller (ou Job) → Facade → Service / ServiceTransactional / ServiceApplication / ServiceSingleton
                                 → Repository / IntercommService / Publisher
```

- **Controller** injeta **Facade** quando a operação orquestra mais de um domínio, ou **Service** quando é uma regra só. Nunca injeta Repository, `IntercommService`, Publisher nem outro Controller.
- **Facade** injeta Services, `ServiceTransactional`, `ServiceApplication`, `ServiceSingleton`, `IntercommService` e Publisher. Não injeta Repository — se precisa de um dado, há um Service dono dele.
- **Service** injeta Repository, `IntercommService`, Publisher, `ServiceApplication`, `ServiceSingleton` e outros Services de responsabilidade própria (composição, ver Service abaixo). Nunca injeta Facade nem Controller.
- **ServiceTransactional** injeta Repository e Services que **só tocam o banco**. Nunca `IntercommService` nem Publisher: I/O externo não entra na transação (ver "Transações" em `code-style.md`).
- **ServiceApplication** não injeta nada que faça I/O — é cálculo puro.
- **Repository**, **IntercommService** e **Publisher** são **ponta final**: só injetam o cliente técnico (`EntityManager`/`JPAQueryFactory`/`JdbcClient` no Repository; o `Client` `@HttpExchange` no IntercommService; o template do broker no Publisher).

Dependência na direção contrária (Repository chamando Service, Service chamando Facade) ou atalho (Controller indo direto no Repository) é sinal de responsabilidade na camada errada — mova a lógica, não crie o atalho.

## Injeção de dependência — por construtor, campo `final`

Toda classe nova injeta pelo construtor, com os campos `private final`, e o construtor gerado pelo Lombok:

```java
@Service
@RequiredArgsConstructor
public class DocumentoFinanceiroServiceImpl implements DocumentoFinanceiroService {

    private final DocumentoFinanceiroRepository documentoFinanceiroRepository;

    private final CalculoJurosServiceApplication calculoJurosServiceApplication;

}
```

Por que construtor, e não `@Autowired` no campo como a base inteira faz:

- **A dependência fica obrigatória e imutável.** O objeto não existe sem ela, e ninguém a troca depois. Com `@Autowired` no campo, a classe pode ser instanciada (em teste, em código manual) com o campo nulo, e o erro aparece como `NullPointerException` longe da causa.
- **Teste sem Spring e sem reflection.** O unitário faz `new XxxServiceImpl(mockA, mockB)` ou usa `@InjectMocks`, que escolhe o construtor. Nada de container para testar um `if`.
- **Ciclo falha na subida.** O Spring Boot proíbe dependência circular por padrão, e com construtor não há como contornar: o ciclo aparece no deploy, não em produção. A base usa `@Lazy` em mais de mil classes justamente para esconder ciclos — `@Lazy` para quebrar ciclo é proibido; o ciclo é desenho errado (duas classes que se chamam mutuamente costumam ser uma responsabilidade partida ao meio, ou estar pedindo uma terceira).
- **Excesso de dependência fica visível.** Um construtor com 12 parâmetros é o aviso de que a classe faz coisa demais (ver `solid.md`).

Detalhes:

- Classe com **um** construtor não leva `@Autowired` — o Spring o usa sozinho.
- `@RequiredArgsConstructor` inclui só os campos `final` (e `@NonNull`). Campo que não é dependência (constante, estado) é `static final` ou inicializado na declaração.
- **`@Qualifier` com Lombok precisa de configuração.** O Lombok não copia anotação do campo para o parâmetro do construtor, a menos que esteja listada no `lombok.config` da raiz do projeto — sem isso, o `@Qualifier` do campo é ignorado em silêncio e o Spring reclama de dois candidatos (ou pior, injeta o único que sobrou):

  ```properties
  # lombok.config
  config.stopBubbling = true
  lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Qualifier
  lombok.copyableAnnotations += org.springframework.beans.factory.annotation.Value
  lombok.copyableAnnotations += br.com.empresa.pagamento.qualifier.PagamentoCartaoPOS
  ```

  Todo `@Qualifier` próprio entra na lista. A alternativa, numa classe isolada, é escrever o construtor à mão.
- `@Value` em campo `final` segue a mesma regra; mas configuração com mais de um valor vai num `@ConfigurationProperties` (ver `code-style.md`), injetado como qualquer bean.
- **`ObjectProvider<T>`** é a forma de injetar algo opcional ou de escopo menor que o do bean — não `@Lazy`.

## Controller — entrada da API, só orquestra a chamada, zero regra de negócio

Interface em `api` com **todo** o contrato (rota, verbo, parâmetros, validação, OpenAPI); implementação em `api.impl` com `@RestController`, que só registra a entrada e delega:

```java
@Tag(name = OpenApiTagConstants.VENDAS_COMANDA_ELETRONICA)
@RequestMapping(path = RotaConstants.ComandaEletronica.BASE, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ComandaEletronicaController {

    @PostMapping(path = RotaConstants.ComandaEletronica.INTEGRAR, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Integrar comanda eletrônica")
    public abstract ResponseEntity<Void> integrar(
            @Parameter(description = "Código da empresa") @RequestHeader(HeaderConstants.CODIGO_EMPRESA) Long codigoEmpresa,
            @Valid @RequestBody IntegrarComandaEletronicaDTO integrarComandaEletronicaDTO);

    @GetMapping(RotaConstants.ComandaEletronica.ADQUIRIR_NUMERO_COMANDA)
    @Operation(summary = "Adquirir comanda eletrônica pelo número")
    public abstract ResponseEntity<ComandaEletronicaDTO> adquirir(
            @Parameter(description = "Código da empresa") @RequestHeader(HeaderConstants.CODIGO_EMPRESA) Long codigoEmpresa,
            @Parameter(description = "Número da comanda") @PathVariable Long numeroComanda);

}
```

```java
@Slf4j
@RestController
@RequiredArgsConstructor
public class ComandaEletronicaControllerImpl implements ComandaEletronicaController {

    private final ComandaEletronicaFacade comandaEletronicaFacade;

    private final ComandaEletronicaService comandaEletronicaService;

    @Override
    public ResponseEntity<Void> integrar(Long codigoEmpresa, IntegrarComandaEletronicaDTO integrarComandaEletronicaDTO) {

        log.info("Integrando a comanda {} da empresa {}", integrarComandaEletronicaDTO.numeroComanda(), codigoEmpresa);

        this.comandaEletronicaFacade.integrar(codigoEmpresa, integrarComandaEletronicaDTO);

        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ComandaEletronicaDTO> adquirir(Long codigoEmpresa, Long numeroComanda) {

        log.info("Adquirindo a comanda {} da empresa {}", numeroComanda, codigoEmpresa);

        return ResponseEntity.ok(this.comandaEletronicaService.adquirir(codigoEmpresa, numeroComanda));
    }

}
```

Regras:

- **Tudo do contrato fica na interface** — `@RequestMapping`/`@XxxMapping`, `@RequestHeader`/`@PathVariable`/`@RequestBody`, `@Valid`, `@Tag`/`@Operation`/`@Parameter`/`@ApiResponse`. O Spring MVC lê as anotações de parâmetro declaradas na interface. A `Impl` não repete nenhuma delas: duplicar é o que deixa as duas divergirem (a base tem `@RequestHeader` na `Impl` e `@ApiParam` na interface, e os nomes nem sempre batem).
- **`@RestController`**, nunca `@Controller` com `ResponseEntity`.
- **Retorno `ResponseEntity<T>`**, como a base faz: deixa explícito o status de sucesso (`ok`, `noContent`, `created`). Valor primitivo sai embrulhado num DTO (`ValorBooleanoDTO`), nunca `Boolean` cru no corpo. Consulta que pode não encontrar resultado e para a qual "vazio" é resposta normal: `.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build())`.
- **Log de entrada fica no Controller**, uma linha, com os **identificadores** recebidos (`codigoEmpresa`, `codigoPDV`, número do documento) — não se repete no Service nem na Facade. Nunca o DTO inteiro: ele quase sempre carrega dado pessoal (ver "Logs" em `code-style.md`). Se o projeto já tem a frase padrão (`EXECUTANDO_METODO`), use-a em vez de inventar outra.
- **Rota publicada é contrato.** Caminho, verbo, parâmetros e corpo de uma rota que já foi para produção não mudam sem combinar com quem consome — e aqui quem consome inclui o **PDV instalado no cliente**, que não se atualiza junto com o servidor. Reorganizar constantes, pacote ou documentação é livre; mudar o que trafega, não. Quando a mudança incompatível for inevitável, use o versionamento de API nativo do Spring Framework 7 (atributo `version` do mapeamento + `ApiVersionConfigurer`), combinado com o time.
- **Rota sempre de constante, nunca texto.** Todas as rotas do serviço ficam em `RotaConstants`, com uma classe interna por controller (o nome do controller sem o sufixo). `BASE` é o `@RequestMapping` da interface; as demais são relativas a ele e levam o nome do próprio caminho, com o parâmetro quando houver:

  ```java
  public final class RotaConstants {

      private RotaConstants() {

      }

      public static final String CONTEXTO = "/pedido-api";

      public static final class ComandaEletronica {

          private ComandaEletronica() {

          }

          public static final String BASE = CONTEXTO + "/comandaEletronica";
          public static final String INTEGRAR = "/integrar";
          public static final String ADQUIRIR_NUMERO_COMANDA = "/adquirir/{numeroComanda}";
      }
  }
  ```

  O motivo é o código que **compara rota como texto**: a lista de rotas públicas do `SecurityFilterChain`, o filtro de auditoria, a guarda de rota sensível. Com o texto repetido no `requestMatchers(...)`, renomear a rota deixa a regra de segurança olhando para um caminho que não existe — e isso não dá erro, só deixa de proteger. Com a constante, a regra usa exatamente o mesmo valor (`RotaConstants.ComandaEletronica.BASE + RotaConstants.ComandaEletronica.INTEGRAR`). Num projeto que já tem a classe única de constantes (`PATH_*`), siga-a; não espalhe uma segunda convenção.

  Fica de fora só o caminho do `Client` `@HttpExchange`: ele descreve a rota de **outro** sistema.
- **Header de contexto também é constante** (`HeaderConstants.CODIGO_EMPRESA`, `HeaderConstants.CODIGO_PDV`). O nome do header é contrato com o PDV tanto quanto a rota.
- **Grupo do Swagger por domínio de negócio, de constante.** Todo controller declara `@Tag(name = OpenApiTagConstants.X)`, no formato `Domínio · Assunto` (ex.: `"Vendas · Comanda eletrônica"`). O domínio é a área de negócio, não a tela. Rota de outro domínio dentro do controller (tipicamente a chamada máquina a máquina) leva o `@Tag` no **próprio método**. Título, descrição e ordem dos grupos ficam uma vez só, num `@OpenAPIDefinition(info = ..., tags = {...})` numa classe `@Configuration` (`OpenApiConfig`).
- **Resposta comum a todos os endpoints vai uma vez só.** O formato de erro (400, 401, 500) se declara uma vez — `@ApiResponse` no topo da interface ou um `OpenApiCustomizer` global —, não copiado em cada método. Se o projeto tem uma anotação composta para isso (`@PDVResponses`), use-a.
- **Descrição de documentação é curta e diz o que é**, não como funciona: `"Código da empresa"`, não `"Código da empresa, validado contra o usuário"`.
- **Verbo HTTP:** `@PostMapping`, `@GetMapping` e `@DeleteMapping`. `GET` **nunca** altera estado — proxy, navegador e cliente HTTP repetem `GET` à vontade, e o log de acesso guarda a URL inteira. Ação de negócio (`integrar`, `efetuarBaixa`, `atualizarLimite`) é `POST`. Evite `PUT`/`PATCH`: a maior parte dos endpoints é ação, não atualização genérica de recurso.
- **`codigoEmpresa` vindo de header é entrada do cliente, não identidade.** Antes de usá-lo, ele é conferido contra o usuário autenticado — uma vez, num `HandlerInterceptor`, não repetido em cada Service (ver "Dado que vem do cliente é validado no servidor" em `security-and-review.md`). Em contrato novo, derive a empresa da identidade (claim do token).
- **`@Valid` no `@RequestBody`, sempre.** Validação de parâmetro simples (`@RequestHeader @Positive Long codigoEmpresa`) também funciona: o Spring Framework 7 valida restrições declaradas nos parâmetros do método do controller sem `@Validated` na classe, e a falha chega como `HandlerMethodValidationException` (tratada no handler global, abaixo).
- **Autorização** fica no `SecurityFilterChain` por rota (de constante); papel específico de um método vai em `@PreAuthorize` **na `Impl`** (com `@EnableMethodSecurity`).
- Nunca validação de regra de negócio no Controller — isso é do Service.

## Facade — orquestra vários Services, quem o Controller chama

A Facade existe quando uma operação de entrada **compõe responsabilidades de domínios diferentes**: busca dado de várias fontes, decide o fluxo, chama a escrita transacional e, depois do commit, a integração externa. Os Services individuais não se conhecem; quem os junta é a Facade.

```java
public interface PedidoFacade {

    public abstract RetornoPedidoDTO integrar(Long codigoEmpresa, PedidoDTO pedidoDTO);

}
```

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoFacadeImpl implements PedidoFacade {

    private final ClienteService clienteService;

    private final PedidoServiceTransactional pedidoServiceTransactional;

    private final NotaFiscalIntercommService notaFiscalIntercommService;

    @Override
    public RetornoPedidoDTO integrar(Long codigoEmpresa, PedidoDTO pedidoDTO) {

        ClienteVO clienteVO = this.clienteService.adquirirAtivo(codigoEmpresa, pedidoDTO.codigoCliente());

        Pedido pedido = this.pedidoServiceTransactional.gravar(codigoEmpresa, clienteVO, pedidoDTO);

        this.notaFiscalIntercommService.solicitarEmissao(codigoEmpresa, pedido.getIdPedido());

        return new RetornoPedidoDTO(pedido.getIdPedido());
    }

}
```

Regras:

- Sufixo `Facade`/`FacadeImpl`, pacote `service.facade`/`service.facade.impl`, `@Service`.
- **Sem `@Transactional`.** A Facade é justamente o lugar onde a escrita (dentro do `ServiceTransactional`) e a chamada externa (fora dele) ficam em passos separados. No exemplo, se a emissão da nota falhar, o pedido já está gravado — e isso é deliberado: a falha pós-commit se trata isoladamente (reenvio, pendência), nunca desfazendo o commit (ver "Transações" em `code-style.md`).
- **Facade não tem regra de negócio própria** além de decidir a ordem e o desvio do fluxo. Cálculo, validação e montagem ficam nos Services que ela chama.
- Facade pode chamar outra Facade **de outro domínio** quando a operação é genuinamente composta (a base tem casos); nunca a si mesma por um caminho indireto.
- Operação de uma regra só não precisa de Facade — o Controller chama o Service direto. Não crie `XxxFacade` que só repassa para `XxxService`.

## Job — entrada para rotina agendada, sem Controller HTTP

```java
@Component
@RequiredArgsConstructor
public class FechamentoMensalJob {

    private final FechamentoMensalFacade fechamentoMensalFacade;

    @Scheduled(cron = "${fechamento-mensal.cron}", zone = AgendamentoConstants.FUSO_BRASILIA)
    @SchedulerLock(name = AgendamentoConstants.FECHAMENTO_MENSAL, lockAtMostFor = AgendamentoConstants.FECHAMENTO_MENSAL_DURACAO_MAXIMA)
    public void executar() {

        this.fechamentoMensalFacade.processar();
    }

}
```

- Pacote `job`, sufixo **`Job`**, `@Component`. Nos projetos que já têm `service.schedule` com `XxxScheduleImpl`, siga o projeto.
- O `Job` ocupa o papel do Controller: injeta Facade ou Service, nunca Repository, e não tem regra de negócio.
- **Cron vem de configuração e o fuso é explícito.** `cron = "${fechamento-mensal.cron}"` lê o horário das propriedades (muda por ambiente sem rebuild). Sem `zone`, o cron é avaliado no fuso do host — em container, UTC — e o fechamento "da 1h" roda às 22h de Brasília do dia anterior. `AgendamentoConstants.FUSO_BRASILIA` é a `String` `"America/Sao_Paulo"` (atributo de anotação não aceita `ZoneId`). Intervalo fixo (`fixedDelayString = "${...}"`) também vem de configuração, em `Duration` (`5m`), nunca número solto de milissegundos.
- **Com mais de uma réplica, o `@Scheduled` dispara em todas.** Rotina que não pode rodar em paralelo leva trava entre instâncias — `@SchedulerLock` do ShedLock no exemplo; alternativas em `cloud-native.md`.
- **Sem estado mutável no Job** (`primeiraExecucao = false` em campo): ele é singleton e, com virtual threads, execuções podem se sobrepor. Atraso inicial é `initialDelayString`, não flag.
- Retry automático não se aplica — se a rotina falhar no meio (metade das empresas processadas), o reprocessamento é tratado no Service (idempotência por empresa, checkpoint).

## Service — onde mora a regra de negócio, responsabilidade única

Interface + `XxxServiceImpl` anotada `@Service`, retorno do tipo puro (`void`, o DTO/VO, `List<...>`), chamada bloqueante.

Um Service mapeia uma agregação de domínio (não necessariamente 1:1 com uma tabela) e tem **uma responsabilidade única e bem isolada**. Se `PedidoService` começa a calcular frete, a regra pertence a `FreteService` — delegue, não absorva. Tamanho saudável: poucos métodos públicos e ~150 linhas de implementação.

**Um Service pode injetar outro Service quando a sub-regra é uma responsabilidade genuinamente diferente** — é composição, não violação de SRP. `LoginService` injetando `AntifraudeService` está certo: antifraude tem regra própria, evolui sozinha e é reusada por outros fluxos. O sinal de problema é o Service injetado sem responsabilidade nomeável (`LoginHelperService`), criado só para "tirar linhas" — aí o caminho é método privado (ver "Tamanho de métodos" em `code-style.md`). Quando a composição atravessa domínios diferentes e decide fluxo, ela é da **Facade**.

**Registro não encontrado** é decidido uma vez: ou o Repository devolve `Optional` e o Service transforma a ausência em erro de negócio, ou o Service devolve `Optional` porque a ausência é resultado normal. `null` não atravessa camada.

```java
@Override
public ComandaEletronicaDTO adquirir(Long codigoEmpresa, Long numeroComanda) {

    ComandaEletronica comandaEletronica = this.comandaEletronicaRepository
            .findFirstByCodigoEmpresaAndNumeroComandaAndDataExclusaoIsNull(codigoEmpresa, numeroComanda)
            .orElseThrow(() -> getBusinessServiceException(COMANDA_ELETRONICA_NAO_ENCONTRADA));

    return this.comandaEletronicaMapper.map(comandaEletronica);
}
```

## Sub-camadas de Service

### `service.transactional` — dono do limite transacional

Sufixo `ServiceTransactional`. É a **única** camada com `@Transactional`, sempre o do **Spring** (`org.springframework.transaction.annotation.Transactional`), sempre **no método** e com `rollbackFor = Exception.class` — o default do Spring só reverte em `RuntimeException` e `Error`, e uma exceção checada que escape deixaria a escrita parcial commitada.

```java
@Service
@RequiredArgsConstructor
public class IntegracaoPedidoServiceTransactionalImpl implements IntegracaoPedidoServiceTransactional {

    private final IntegracaoCabecalhoService integracaoCabecalhoService;

    private final IntegracaoItensService integracaoItensService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void integrar(ChaveLogicaPedidoVO chaveLogicaPedidoVO, DocumentoEletronicoPedidoDTO documentoEletronicoPedidoDTO) {

        this.integracaoCabecalhoService.integrar(documentoEletronicoPedidoDTO);

        this.integracaoItensService.integrar(chaveLogicaPedidoVO, documentoEletronicoPedidoDTO);
    }

}
```

- **Nunca `jakarta.transaction.Transactional`.** O Spring aceita as duas, mas misturar significa dois atributos com nome parecido (`rollbackOn` × `rollbackFor`) e semântica de propagação diferente na mesma base — a revisão não tem como saber qual valeu. A base tem as duas misturadas; código novo usa só a do Spring.
- **Nunca `@Transactional` na classe** — fica invisível qual método abre transação, e todo método novo herda uma transação que talvez não devesse ter.
- **Leitura que precisa de consistência entre várias consultas** usa `@Transactional(readOnly = true)` no `ServiceTransactional`. Leitura simples não precisa: o método do Spring Data já roda em transação somente-leitura sozinho.
- **Propagação e isolamento padrão.** `REQUIRES_NEW` e isolamento customizado só com motivo escrito no código — `REQUIRES_NEW` abre uma segunda conexão enquanto a primeira está presa, e sob carga isso esgota o pool.
- Essa camada resolve de forma estrutural a **auto-invocação**: chamar um método `@Transactional` da própria classe (`this.gravar(...)`) não passa pelo proxy do Spring e a transação é ignorada em silêncio. Como o método transacional mora em **outra** classe, a chamada sempre passa pelo proxy.
- Chamada a `IntercommService`/Publisher fica **fora**: quem precisa de dado externo é a Facade, antes de chamar o `ServiceTransactional`; quem avisa o mundo é a Facade, depois.

### `service.application` — cálculo e derivação pura

Sufixo `ServiceApplication`. Deriva ou calcula a partir do que recebe: montar a chave lógica de um documento, calcular pontuação, ratear desconto. **Não injeta nada que faça I/O** e não persiste — por isso é o código mais barato de testar da base.

```java
public interface CalculoPontuacaoServiceApplication {

    public abstract BigDecimal calcularPercentual(List<VendaItemPontuacaoVO> listaVendaItemPontuacaoVO, BigDecimal valorTotalVenda);

}
```

Se começar a ter consulta ao banco ou `if` de regra que decide o fluxo, virou Service.

### `service.singleton` — estado em memória, raro

Todo bean do Spring já é singleton; o sufixo `ServiceSingleton` marca o que **guarda estado mutável entre requisições** (cache de configuração lida do banco). `@Scope(SCOPE_SINGLETON)` é redundante e não se escreve.

```java
@Service
@RequiredArgsConstructor
public class ConfiguracaoApresentacaoServiceSingletonImpl implements ConfiguracaoApresentacaoServiceSingleton {

    private final Map<ChaveEmpresaPDVVO, ConfiguracaoApresentacaoVO> mapaConfiguracaoApresentacao = new ConcurrentHashMap<>();

    private final ConfiguracaoApresentacaoService configuracaoApresentacaoService;

    @Override
    public ConfiguracaoApresentacaoVO adquirir(Long codigoEmpresa, Long codigoPDV) {

        return this.mapaConfiguracaoApresentacao.computeIfAbsent(new ChaveEmpresaPDVVO(codigoEmpresa, codigoPDV),
                this.configuracaoApresentacaoService::adquirir);
    }

    @Override
    public void limpar() {

        this.mapaConfiguracaoApresentacao.clear();
    }

}
```

Três perguntas antes de criar um:

1. **Precisa mesmo viver além da requisição?** Se não, não é Singleton.
2. **A operação é atômica?** `HashMap` com `containsKey` → `put` (padrão que existe na base) é corrida: duas requisições carregam juntas, e um `put` concorrente com `get` pode corromper o mapa. `ConcurrentHashMap.computeIfAbsent` resolve a atomicidade — com o cuidado de que a carga roda segurando a trava daquele trecho do mapa, então ela precisa ser rápida e não pode mexer no próprio mapa. `@Synchronized` só no `limpar()` não protege nada.
3. **Existe alternativa sem estado próprio?** Na maioria dos casos, sim: **`@Cacheable`** com Caffeine (TTL, tamanho máximo, `sync = true` para uma carga por chave) faz o mesmo com menos código e menos risco. A chave do cache **inclui o tenant** (`codigoEmpresa`), sempre.

**Variante um escritor, muitos leitores** (um Job recarrega, o resto só lê): troque a referência inteira (`AtomicReference`, ou campo `volatile` reatribuído com coleção nova imutável), em vez de `.clear()` + `.putAll()` — o leitor nunca vê o mapa vazio no meio da recarga.

**Escala horizontal:** cada réplica tem o seu mapa. Aceitável para dado praticamente imutável; para dado que muda em runtime, ver `cloud-native.md`.

### Executor — trabalho paralelo isolado

- **`XxxServiceExecutor`** (em `service`) — interface + Impl normais, para o fluxo que dispara várias unidades de trabalho.
- **`XxxExecutor implements Callable<T>`** (em `service.application`) — objeto de trabalho puro, **sem Spring**, construído à mão com o `ServiceTransactional` e os dados no construtor:

```java
public class FechamentoFinanceiroExecutor implements Callable<Long> {

    private final FechamentoFinanceiroServiceTransactional fechamentoFinanceiroServiceTransactional;

    private final FechamentoFinanceiroChaveLogicaVO fechamentoFinanceiroChaveLogicaVO;

    public FechamentoFinanceiroExecutor(FechamentoFinanceiroServiceTransactional fechamentoFinanceiroServiceTransactional,
            FechamentoFinanceiroChaveLogicaVO fechamentoFinanceiroChaveLogicaVO) {

        this.fechamentoFinanceiroServiceTransactional = fechamentoFinanceiroServiceTransactional;
        this.fechamentoFinanceiroChaveLogicaVO = fechamentoFinanceiroChaveLogicaVO;
    }

    @Override
    public Long call() throws Exception {

        return this.fechamentoFinanceiroServiceTransactional.gravarCabecalho(this.fechamentoFinanceiroChaveLogicaVO);
    }

}
```

Os campos são `final` porque o objeto é criado numa thread e executado em outra — `final` é o que garante, pelo modelo de memória do Java, que a outra thread enxergue os campos preenchidos. Cada `call()` roda sob a transação do `ServiceTransactional` que chama; não existe transação compartilhada entre tarefas. A mecânica de submissão está em `threads-and-virtual-threads.md`.

## Repository — Spring Data, um por entidade

```java
public interface ComandaEletronicaRepository extends CrudRepository<ComandaEletronica, Long>, ComandaEletronicaRepositoryCustom {

    public abstract Optional<ComandaEletronica> findFirstByCodigoEmpresaAndNumeroComandaAndDataExclusaoIsNull(Long codigoEmpresa, Long numeroComanda);

    public abstract List<ComandaEletronica> findByCodigoEmpresaAndSituacaoAndDataExclusaoIsNull(Long codigoEmpresa,
            SituacaoComandaEletronicaEnum situacaoComandaEletronicaEnum);

}
```

```java
public interface ComandaEletronicaRepositoryCustom {

    public abstract List<ComandaEletronicaResumoProjection> listarPorFiltro(Long codigoEmpresa, FiltroComandaEletronicaDTO filtroComandaEletronicaDTO);

}
```

```java
@RequiredArgsConstructor
public class ComandaEletronicaRepositoryImpl implements ComandaEletronicaRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<ComandaEletronicaResumoProjection> listarPorFiltro(Long codigoEmpresa, FiltroComandaEletronicaDTO filtroComandaEletronicaDTO) {

        ...
    }

}
```

Regras:

- **Base `CrudRepository<Entidade, Long>`.** `JpaRepository`/`PagingAndSortingRepository` só quando o método extra for usado de fato. Cada interface herdada põe na mão do Service operações **sem filtro de tenant** (`findAll`, `findAllById`, `deleteAll`, `count`) — para tabela por empresa, considere estender `Repository<Entidade, Long>` (vazio) e declarar só o que o negócio usa (ver "I" em `solid.md` e "Isolamento entre tenants" em `security-and-review.md`).
- **Sem `@Repository`** na interface — o Spring Data a detecta sozinho. Na `RepositoryImpl` também não é preciso: o Spring Data a encontra pelo nome.
- **O nome da `Impl` é o nome do Repository + `Impl`** (`ComandaEletronicaRepositoryImpl`), e o Repository **estende** o `RepositoryCustom`. Assim o Service injeta um tipo só — `ComandaEletronicaRepository` — e enxerga o método derivado e o customizado juntos. Projeto que injeta `XxxRepository` e `XxxRepositoryCustom` separados pode continuar assim; não misture as duas formas num mesmo Repository.
- **Método derivado** (nome em inglês, é a query) para consulta simples de uma tabela; quando o nome passa de três condições ou fica ilegível, vai para o `RepositoryCustom` com nome de negócio em português. Escolha do caminho (derivado × `@Query` × QueryDSL × `JdbcClient`) em `database.md`.
- **Busca única devolve `Optional`**; lista nunca devolve `Optional` nem `null` — lista vazia é o "não encontrado" dela.
- **Todo método de consulta em tabela por empresa recebe `codigoEmpresa`** (e `codigoPDV`, quando a tabela é por PDV), inclusive o que busca por um id que "já é único": o par (tenant, id) identifica o recurso; id sozinho permite ler dado de outra empresa enumerando valores. A exceção é **tabela global**, sem `cd_empresa`. O critério é o schema: se a tabela tem a coluna, o filtro é obrigatório. `findById` herdado do `CrudRepository` **não filtra empresa** — em tabela por empresa, não o use a partir de dado vindo do cliente.
- **Soft delete:** tabela com `dt_exclusao` filtra `DataExclusaoIsNull` em toda consulta de negócio.
- **A única injeção do `RepositoryImpl` é o cliente técnico** (`JPAQueryFactory`, `EntityManager`, `JdbcClient`) — nunca um Service.
- **Sem `@Transactional` no Repository.** Exclusão em massa ou `UPDATE` do `RepositoryCustom` roda dentro da transação de quem chama (o `ServiceTransactional`).

## Client e IntercommService — chamada externa síncrona

O **Client** é o transporte (a interface `@HttpExchange`); o **IntercommService** é quem o Service ou a Facade chama, e esconde deles o detalhe do parceiro: autenticação, formato, tradução de erro, tempo limite e resiliência.

```java
@HttpExchange(accept = MediaType.APPLICATION_JSON_VALUE, contentType = MediaType.APPLICATION_JSON_VALUE)
public interface CobrancaGatewayClient {

    @PostExchange("/cobrancas")
    public abstract RetornoCobrancaGatewayDTO gerar(@RequestBody CobrancaGatewayDTO cobrancaGatewayDTO);

    @GetExchange("/cobrancas/{identificadorTransacao}")
    public abstract RetornoCobrancaGatewayDTO consultar(@PathVariable String identificadorTransacao);

}
```

```java
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = IntercommConstants.GRUPO_COBRANCA_GATEWAY, types = CobrancaGatewayClient.class)
public class HttpClientConfig {

}
```

```properties
#HTTP Client - gateway de cobranca
spring.http.serviceclient.cobranca-gateway.base-url=${COBRANCA_GATEWAY_URL}
spring.http.serviceclient.cobranca-gateway.connect-timeout=2s
spring.http.serviceclient.cobranca-gateway.read-timeout=5s
```

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class CobrancaGatewayIntercommServiceImpl implements CobrancaGatewayIntercommService {

    private final CobrancaGatewayClient cobrancaGatewayClient;

    @Override
    @Retryable(includes = ResourceAccessException.class, maxRetries = ResilienciaConstants.TENTATIVAS_CONSULTA,
            delay = ResilienciaConstants.ESPERA_INICIAL_MS, multiplier = ResilienciaConstants.MULTIPLICADOR_ESPERA)
    public RetornoCobrancaGatewayDTO consultar(String identificadorTransacao) {

        return this.cobrancaGatewayClient.consultar(identificadorTransacao);
    }

    @Override
    public RetornoCobrancaGatewayDTO gerar(CobrancaGatewayDTO cobrancaGatewayDTO) {

        try {

            return this.cobrancaGatewayClient.gerar(cobrancaGatewayDTO);

        } catch (HttpClientErrorException httpClientErrorException) {

            log.warn("Gateway de cobrança recusou a cobrança com status {}", httpClientErrorException.getStatusCode());
            throw getBusinessServiceException(COBRANCA_RECUSADA_GATEWAY);

        } catch (HttpServerErrorException | ResourceAccessException excecaoIndisponibilidade) {

            log.error("Gateway de cobrança indisponível ao gerar a cobrança", excecaoIndisponibilidade);
            throw getBusinessServiceException(GATEWAY_COBRANCA_INDISPONIVEL);
        }
    }

}
```

Regras:

- **Client**: interface `@HttpExchange` em `intercomm.client`, sufixo `Client`, **sem URL fixa** — o endereço vem do `base-url` do grupo, por variável de ambiente. O Client não tem `Impl`: o Spring gera o proxy. Grupo por parceiro (`cobranca-gateway`), com o nome em constante.
- **Todo Client tem tempo limite — obrigatório.** Sem `connect-timeout`/`read-timeout`, um parceiro lento prende a thread da requisição; com tráfego, conexões se esgotam e o serviço inteiro para por causa de **uma** integração. Defina também o global (`spring.http.clients.connect-timeout`/`read-timeout`) como rede de segurança.
- **IntercommService**: interface + `Impl` em `intercomm.service`, `@Service`. É a ponta da cadeia: injeta só o Client. Traduz o erro HTTP para o erro de negócio do serviço — `4xx` do parceiro é recusa (regra), `5xx`/timeout é indisponibilidade — sem deixar `RestClientException` subir crua até o handler global. Quando a falha é técnica, a causa original não pode sumir: se a fábrica de exceção do projeto aceita a causa, passe-a; se não aceita, registre-a com `log.error` **no ponto da tradução**, como no exemplo — é o único lugar onde ela ainda existe.
- **`@Retryable` (Spring Framework 7, `org.springframework.resilience.annotation`, habilitado com `@EnableResilientMethods`) só em operação idempotente** — consulta, ou escrita que o parceiro trata como idempotente. Por isso o `gerar` do exemplo não tem retry: repetir uma cobrança que o parceiro já aceitou gera cobrança duplicada. Só aplique em escrita depois de confirmar idempotency key ou consulta de status antes de reenviar.
- **Retry nunca repete erro de negócio.** Restrinja com `includes` à falha transitória de rede (`ResourceAccessException`) — sem isso, o default repete **qualquer** exceção, inclusive "não encontrado", três vezes. Os números vêm de constante (atributo de anotação exige constante de compilação; por isso são `int`/`long` primitivos, a única exceção à regra do wrapper).
- **Circuit breaker** (parar de chamar o parceiro que está caindo) não existe no core do Spring: use Resilience4j, com a versão compatível com o Boot em uso, no método do IntercommService. Com o circuito aberto, a falha é indisponibilidade (503).
- **`@ConcurrencyLimit`** no método do IntercommService limita quantas chamadas simultâneas vão ao parceiro — essencial com virtual threads, que removem o limite natural do pool de threads (ver `threads-and-virtual-threads.md`).
- **URL dinâmica por destino** (o servidor chamando cada PDV pelo endereço dele): monte o proxy com `HttpServiceProxyFactory` sobre um `RestClient` construído a partir do `RestClient.Builder` do Spring (que já traz timeouts e observabilidade), e guarde por endereço num `ConcurrentHashMap` — não crie um `RestClient` novo a cada chamada. Essa montagem fica numa classe `XxxClientFactory` em `intercomm.client`.
- **Log do cliente HTTP nunca em nível de corpo/header em produção** — o header leva credencial (Basic, Bearer).
- Erro de parceiro em teste: o `IntercommService` é a fronteira que se mocka no teste do Service (ver `testing.md`).

## Publisher — dispara assíncrono, não espera resposta

Quando o fluxo só precisa **avisar** algo para ser processado depois, em outro processo (fila/tópico), use `XxxPublisher` + `XxxPublisherImpl` em `publisher`/`publisher.impl`. O payload leva sufixo **`Event`**.

```java
@Service
@RequiredArgsConstructor
public class PedidoAprovadoPublisherImpl implements PedidoAprovadoPublisher {

    private final KafkaTemplate<String, PedidoAprovadoEvent> kafkaTemplate;

    @Override
    public void publicar(PedidoAprovadoEvent pedidoAprovadoEvent) {

        try {

            this.kafkaTemplate.send(MensageriaConstants.TOPICO_PEDIDO_APROVADO, pedidoAprovadoEvent.chaveIdempotencia(), pedidoAprovadoEvent)
                    .get(ResilienciaConstants.TEMPO_LIMITE_PUBLICACAO_SEGUNDOS, TimeUnit.SECONDS);

        } catch (InterruptedException interruptedException) {

            Thread.currentThread().interrupt();
            log.error("Publicação do pedido aprovado interrompida", interruptedException);
            throw getBusinessServiceException(PUBLICACAO_INTERROMPIDA);

        } catch (ExecutionException | TimeoutException excecaoPublicacao) {

            log.error("Falha ao publicar o pedido aprovado", excecaoPublicacao);
            throw getBusinessServiceException(FALHA_PUBLICACAO_EVENTO);
        }
    }

}
```

- O template do broker (`KafkaTemplate`, `RabbitTemplate`, `PubSubTemplate`, `SqsTemplate`) é o cliente técnico — infraestrutura, não dependência de camada. A base hoje não tem broker; o exemplo usa Kafka só para mostrar a forma.
- **O envio é assíncrono e a falha não sobe sozinha.** Ignorar o `CompletableFuture` devolvido é perder evento em silêncio; o exemplo espera a confirmação com tempo limite. Se o requisito for "melhor esforço", registre a falha com `whenComplete` + `log.error`, deixando a decisão explícita.
- **Fora de qualquer `@Transactional`**, como o IntercommService.
- **Gravar no banco e publicar não é atômico.** Quando o evento não pode se perder (financeiro, fiscal), grave-o numa tabela de saída na **mesma transação** da escrita e publique a partir dela num passo separado (padrão *outbox*).
- **O consumidor é idempotente** — entrega "at-least-once" repete mensagem. Documente no `Event` o campo de deduplicação.
- **Sem broker, dentro do mesmo processo:** `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` é o jeito do Spring de rodar algo **só depois do commit** — útil para disparar a integração sem a Facade precisar orquestrar. Lembre que, se o processo cair entre o commit e o listener, o evento se perde: não substitui o outbox quando a entrega é obrigatória.

## DTO — contrato de API, sempre singular

Em código novo, DTO é **`record`**:

```java
public record IntegrarComandaEletronicaDTO(

        @Schema(description = "Código da ordem de operação")
        @NotNull
        Long codigoOrdemOperacao,

        @Schema(description = "Número da comanda")
        @NotNull
        @Positive
        Long numeroComanda,

        @Schema(description = "Situação da comanda")
        @NotNull
        SituacaoComandaEletronicaEnum situacao) {

}
```

- **`record` para DTO imutável** — entrada desserializada pelo Jackson e saída montada pelo Mapper. Sem Lombok, sem setter, sem `equals` gerado errado.
- **Montado à mão com muitos campos**, o construtor posicional do `record` vira o mesmo problema do `@AllArgsConstructor`: `new XxxDTO(10L, 25L, 3L, "C")` não diz qual valor vai onde, e acrescentar um campo troca valores de lugar em silêncio quando os tipos coincidem. Nesse caso use `@Builder` do Lombok no `record` (`XxxDTO.builder().numeroComanda(...).build()`), ou o Mapper. Construtor posicional direto só com até três campos de tipos distintos, ou DTO de campo único (`ValorBooleanoDTO`).
- **Projeto existente com DTO em classe Lombok** (`@Data` + `Serializable`, inclusive os da biblioteca de DTOs compartilhada): mantenha o estilo dele no mesmo projeto, trocando `@Data` por `@Getter @Setter @NoArgsConstructor` ao criar um novo. **Reuse o DTO da biblioteca compartilhada** antes de criar outro igual.
- Sempre singular (`ComandaEletronicaItemDTO`, não `...ItensDTO`). Sem sufixo genérico `Request`/`Response`/`Form`; os prefixos que já são convenção da base (`Retorno*DTO` para resposta, `Api*DTO` para contrato de parceiro) continuam valendo.
- **JSON orientado a objeto:** dois ou mais campos de **outro conceito de negócio** (endereço, contato, empresa) viram um DTO próprio aninhado (`EnderecoDTO endereco`), reutilizável entre listagem e detalhe. Conceito ausente sai `null`, não um objeto com todos os campos nulos. A Projection continua plana; quem agrupa é o Mapper.
- **`Serializable` não é obrigatório** em DTO novo — só quando o objeto vai para cache distribuído ou sessão.
- **Entidade não é DTO.** Ela não sai pela API nem entra como `@RequestBody`.
- Bean Validation no DTO de entrada, em vez de checagem manual de `null` no Service.

## Projection — retorno parcial de query

Fica em `repository.projection`, sufixo `Projection`. Em código novo, `record` (o Spring Data JPA e o `JdbcClient` preenchem pelo construtor/nome da coluna; no QueryDSL, `Projections.constructor(...)`). Uso **interno** (repository → service): para sair pela API, converta para DTO no Service ou no Mapper.

## Entidade JPA — espelho da tabela

Fica em `repository.domain`; chave composta em `repository.domain.pk`; `AttributeConverter` junto da entidade.

```java
@Getter
@Setter
@Entity
@Table(name = "tb_destinatario")
@SequenceGenerator(name = "gen_destinatario", sequenceName = "gen_destinatario", allocationSize = 1)
public class Destinatario implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id_destinatario", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen_destinatario")
    private Long idDestinatario;

    @Column(name = "cd_empresa", nullable = false)
    private Long codigoEmpresa;

    @Column(name = "cd_ibge", nullable = false)
    private Long codigoIBGE;

    @Column(name = "dt_cadastro", nullable = false)
    private LocalDateTime dataCadastro;

    @Column(name = "dt_exclusao")
    private LocalDateTime dataExclusao;

}
```

- `@Getter @Setter`, `implements Serializable` e `serialVersionUID = 1L`, campos `private`. **`@Data` só em entidade sem relacionamento** — com `@ManyToOne`/`@OneToMany`, o `toString`/`equals`/`hashCode` gerados percorrem a associação, disparam carga lazy fora da sessão e, em relação bidirecional, entram em recursão até `StackOverflowError`. Com relacionamento: `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` com `@EqualsAndHashCode.Include` só no id, e `@ToString.Exclude` na associação.
- **Associação é `fetch = FetchType.LAZY`**, inclusive `@ManyToOne` (cujo default é `EAGER`). O que o fluxo precisa vem na consulta (`JOIN FETCH`, `@EntityGraph`, projection), nunca de `enable_lazy_load_no_trans`.
- **Nome do campo Java por extenso, em português**, mesmo com a coluna abreviada (`cd_ibge` → `codigoIBGE`). Por isso `@Column(name = ...)` é obrigatório em todo campo.
- **Data é `LocalDate`/`LocalDateTime`**, sem `@Temporal` (que só existe para `java.util.Date`). Preenchida no Service com o `Clock` de Brasília — `@CreationTimestamp`/`@UpdateTimestamp` usam o relógio da JVM, que em container é UTC (ver "Data, hora e fuso" em `code-style.md`).
- `@SequenceGenerator` com `allocationSize = 1` (casa com o `INCREMENT 1` da sequence; o default do Hibernate é 50) e `name` igual ao `sequenceName`. Nome da sequence e da PK seguem o schema do projeto (`database.md`).
- **Tabela legada** mantém o nome real no `@Table`; o que não se herda é o estilo.
- **Chave composta**: `@EmbeddedId` com classe `XxxPK` (sufixo sem separador) em `repository.domain.pk`, `Serializable`, com `equals`/`hashCode`. Coluna repetida na entidade leva `insertable = false, updatable = false`.
- Enum na coluna: `AttributeConverter` pelo `codigo` (ver `database.md`).

## VO — objeto de domínio interno com regra de negócio

Diferente de DTO e Projection, o VO carrega comportamento:

```java
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DocumentoFinanceiroVO {

    @Builder.Default
    private final BigDecimal valorBaixado = BigDecimalUtil.ZERO;

    private final BigDecimal valorTotal;

    private final LocalDate dataVencimento;

    public Boolean documentoTotalmenteBaixado() {

        return BigDecimalUtil.igual(this.valorBaixado, this.valorTotal);
    }

}
```

Se é só transporte sem comportamento, é DTO ou Projection, não VO. VO de valor simples e imutável (chave lógica, par empresa/PDV usado como chave de mapa) é `record` — `equals`/`hashCode` corretos de graça, o que importa justamente quando ele é chave de `Map`. VO não leva anotação de OpenAPI: ele não é contrato.

## Mapper (MapStruct) — sufixo `Mapper`, pacote `mapper`

Preferência acima de 3 atributos; abaixo disso, conversão manual no Service é aceitável.

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ComandaEletronicaMapper {

    ComandaEletronicaDTO map(ComandaEletronica comandaEletronica);

    List<ComandaEletronicaDTO> map(List<ComandaEletronica> listaComandaEletronica);

}
```

- **MapStruct, não ModelMapper.** O ModelMapper mapeia por reflection em runtime: campo renomeado vira `null` sem erro de compilação, e o build nativo precisa de hint para cada classe. O MapStruct gera código na compilação e, com `unmappedTargetPolicy = ERROR`, campo esquecido **não compila**.
- `componentModel = SPRING` + `injectionStrategy = CONSTRUCTOR`: o Mapper é um bean injetado por construtor como qualquer outro.
- Interface de Mapper é a exceção ao `public abstract` (a implementação é gerada).
- Método `map(...)`; a lista é gerada a partir do método do item.
- **Mapper de entrada (DTO → entidade) ignora campo de controle** — id, `codigoEmpresa`, situação e valor calculado são definidos pelo Service (`@Mapping(target = "idPedido", ignore = true)`). Ver "Campo de controle não vem do corpo" em `security-and-review.md`.
- No `pom.xml`, o `mapstruct-processor` e o `lombok-mapstruct-binding` entram no `annotationProcessorPaths` junto com o Lombok, nessa ordem de dependência (ver `stack-and-build.md`).

## Enum — atributo `codigo` + `getByCodigo`

Padrão da base: o enum guarda o código que está na coluna e na API.

```java
@Getter
@AllArgsConstructor
public enum SituacaoComandaEletronicaEnum {

    CONSUMIDA("C"),
    PAGA("P"),
    CANCELADA("X");

    @JsonValue
    private final String codigo;

    @JsonCreator
    public static SituacaoComandaEletronicaEnum getByCodigo(String codigo) {

        return Arrays.stream(values())
                .filter(situacaoComandaEletronicaEnum -> ObjectUtil.isEquals(situacaoComandaEletronicaEnum.getCodigo(), codigo))
                .findFirst()
                .orElseThrow(() -> getBusinessServiceException(SITUACAO_COMANDA_INVALIDA));
    }

}
```

- Enum "pelado", sem atributo, só quando o nome da constante é o próprio valor gravado.
- `@JsonValue` no atributo e `@JsonCreator` no `getByCodigo` fazem o JSON trafegar o código (`"C"`), não o nome da constante — sem isso, o Jackson usa `CONSUMIDA`. Escolha um e documente no `@Schema`; contrato já publicado não troca.
- Constante de enum se compara com `==`.
- Mapa estático de busca, quando existir, é `private static final` e imutável (`Map.copyOf`/`Collectors.toUnmodifiableMap`) — campo estático mutável é corrida entre threads.

## Exceções e tratamento de erro

### Erro de negócio

Nos projetos com a biblioteca de utilitários compartilhada, o erro de negócio é sempre **`BusinessServiceException`**, criada pela fábrica a partir do **código** da mensagem — o texto vem do arquivo de mensagens técnicas, nunca do código Java:

```java
import static br.com.empresa.util.exception.Resource.getBusinessServiceException;

throw getBusinessServiceException(COMANDA_ELETRONICA_NAO_ENCONTRADA);

throw getBusinessServiceException(FALHA_COMUNICACAO_MONITOR, Map.of(EMPRESA, codigoEmpresa));
```

- O código é uma constante da biblioteca de mensagens técnicas; procure antes de criar, e crie lá (com o texto no `.properties` dela), nunca uma `String` no meio do Service.
- `new BusinessServiceException(...)` direto só onde a mensagem vem pronta de um payload externo (tradução de erro de parceiro no IntercommService).
- **Serviço sem a biblioteca compartilhada** (standalone, como o PIX): exceção própria `BusinessServiceException` em `exception`, construída a partir de um enum de erros (`ErroEnum`) que carrega o **código, a chave da mensagem e o `HttpStatus`**; o texto fica em `messages.properties`, resolvido pelo `MessageSource`. Não escreva o texto da mensagem no próprio enum.
- **Hierarquia unchecked; sem `throws Exception` em assinatura pública.** Exceção checada de biblioteca é capturada onde nasce e relançada como erro de negócio, **preservando a causa** — como causa da nova exceção, se a fábrica aceitar; senão, em `log.error` no ponto da tradução (confira a assinatura da fábrica na versão da biblioteca antes de supor uma sobrecarga). A exceção à regra é contrato da JDK (`Callable.call() throws Exception`).
- **Nada de `catch (Exception e) { if (e instanceof X) ... }`** — use multi-catch (`catch (X | Y excecao)`) ou um `catch` por tipo.

### Handler global — um formato só

Um `@RestControllerAdvice` em `config`, que estende `ResponseEntityExceptionHandler` e devolve **todo** erro no mesmo corpo (`BusinessServiceDTO`: `codigo`, `mensagemTecnica`, `mensagem`; ou o equivalente do projeto standalone):

```java
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessServiceException.class)
    public ResponseEntity<Object> tratarBusinessServiceException(BusinessServiceException businessServiceException) {

        return ResponseEntity.status(this.adquirirStatus(businessServiceException)).body(businessServiceException.getBusinessServiceModel());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> tratarExcecaoNaoPrevista(Exception exception) {

        log.error("Falha não prevista ao processar a requisição", exception);

        return ResponseEntity.internalServerError().body(getBusinessServiceException(ERRO_NAO_TRATADO).getBusinessServiceModel());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders httpHeaders,
            HttpStatusCode httpStatusCode, WebRequest webRequest) {

        return ResponseEntity.status(httpStatusCode).headers(httpHeaders).body(this.montarCorpoErroFramework(exception, httpStatusCode));
    }

}
```

- **`handleExceptionInternal` sobrescrito** é o que faz os erros que o próprio Spring trata — `@Valid` falhando (`MethodArgumentNotValidException`), validação de parâmetro (`HandlerMethodValidationException`), JSON malformado (`HttpMessageNotReadableException`), rota inexistente (`NoResourceFoundException`), método não permitido — saírem **no formato da equipe**, e não no `ProblemDetail` padrão do Spring. Sem isso, metade dos erros da API tem um formato e metade outro. Para `MethodArgumentNotValidException`, o corpo lista campo e mensagem de cada violação.
- Método sobrescrito leva `@Override` — sem ele, um erro de assinatura vira um método novo que nunca é chamado (há caso assim na base).
- **O stack trace fica só no log**, nunca no corpo. `log.error` com a exceção como último argumento, e só para o que é falha de verdade (500) — erro de negócio esperado não é `error`.
- Um handler só por aplicação: não use `@Order(HIGHEST_PRECEDENCE)` para disputar com outro handler, e não ponha `@Lazy` nele.

### Status HTTP

O status é decidido **uma vez, junto com o código do erro** — no `ErroEnum` do serviço standalone, ou num mapa código → status no handler (`adquirirStatus`) quando a exceção da biblioteca não carrega status — nunca por `if` espalhado nem a cada `throw`:

| Situação | Status |
|---|---|
| Entrada inválida (Bean Validation, formato, regra de preenchimento) | 400 |
| Registro não encontrado **dentro do tenant** | 404 |
| Estado não permite a operação (já cancelado, já integrado, duplicado) | 409 |
| Parceiro externo indisponível ou circuito aberto | 503 |
| Falha não prevista | 500 |

**Contrato publicado manda.** Os serviços legados respondem **400 para todo erro de negócio**, e o PDV instalado no cliente trata o erro por esse status. Num endpoint que já existe, não troque 400 por 404/409 sem combinar com quem consome; serviço e rota novos já nascem com a tabela acima. (O PIX responde 503 para tudo, inclusive "não encontrado" — é divergência, e a mesma regra de contrato vale para corrigi-la.)

401 e 403 saem do Spring Security, não de erro de negócio. No fluxo de login, credencial inválida tem a **mesma** mensagem para usuário inexistente e senha errada. Registro de outra empresa responde 404, nunca 403 — 403 confirma que o registro existe.

### Fora do Controller, a validação não dispara sozinha

DTO que chega por outro caminho (resposta de parceiro dentro de um Job, mensagem de fila) não passa pelo `@Valid` do MVC. Injete o `Validator` (`jakarta.validation.Validator`) no Service e valide manualmente, lançando o erro de negócio do padrão.

**Lista opcional** (vazia = "sem filtro") não leva `@NotEmpty`: lista vazia é valor válido.

## Qualifier — desambiguar múltiplas implementações

Quando há mais de uma implementação da mesma interface e o ponto de injeção precisa escolher, crie uma anotação própria em vez de `@Qualifier("nome")` com string solta ou `if` decidindo em runtime:

```java
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.PARAMETER })
public @interface PagamentoCartaoPOS {

}
```

```java
@Service
@PagamentoCartaoPOS
public class PagamentoCartaoPOSServiceImpl implements PagamentoCartaoService { ... }
```

```java
@Service
@RequiredArgsConstructor
public class FinalizacaoVendaServiceImpl implements FinalizacaoVendaService {

    @PagamentoCartaoPOS
    private final PagamentoCartaoService pagamentoCartaoService;

}
```

A anotação precisa estar em `lombok.copyableAnnotations` (ver "Injeção de dependência" acima) — senão o Lombok não a leva para o construtor e o Spring não sabe qual implementação usar.

**Escolha por enum em runtime** (o PSP da cobrança vem do cadastro do cliente): injete `Map<String, Interface>` ou monte um `Map<XxxEnum, Interface>` num `@Bean` e escolha pela chave — nunca `if`/`switch` crescendo a cada implementação nova (ver "O" em `solid.md`).

**Implementação ativa por ambiente** (dois provedores possíveis, um ativo por configuração): `@ConditionalOnProperty` em cada implementação. Ele decide **na subida**, pela propriedade — a mesma imagem serve a todos os ambientes. Não use `@Profile` para isso: perfil é ambiente (local, teste), não escolha de fornecedor.
