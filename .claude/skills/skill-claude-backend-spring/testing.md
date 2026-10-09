# Testes

Referência oficial: **https://docs.spring.io/spring-boot/reference/testing/index.html** (e, para Mockito e mocks de bean, a seção de testes do Spring Framework). Quando esta página e a documentação da versão em uso divergirem, a documentação vence.

## Ponto de partida

A base tem uma suíte grande (quase 2 mil testes no serviço de venda) — em **JUnit 4**, com `@RunWith(MockitoJUnitRunner.StrictStubs.class)`, `@MockBean`, PowerMock e Testcontainers subido à mão. O estilo (Mockito estrito, fixture JSON, Testcontainers com Liquibase) é bom; o ferramental é de uma geração anterior. Teste novo é **JUnit 5 (Jupiter)**; teste antigo migra quando você mexer nele, não em massa.

Dependências (Boot 4 — os starters de teste são separados por tecnologia):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

Confira o nome exato de cada starter e artefato na documentação da versão do Boot — a divisão por módulo é nova no Boot 4 e as anotações de fatia (`@WebMvcTest`, `@DataJpaTest`) mudaram de pacote; deixe a IDE importar a partir da dependência certa em vez de copiar import de projeto Boot 3.

Configure o surefire para rodar **em UTC**, o fuso do container — o bug de data das 21h aparece no build, não à noite em produção:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>@{argLine} -Duser.timezone=UTC</argLine>
    </configuration>
</plugin>
```

O `@{argLine}` preserva o agente do JaCoCo. Sem o provider `surefire-junit47` — ele só roda JUnit 4.

## Nomenclatura

- **Classe**: classe testada + `Test` (`ClienteServiceImplTest` quando testa a `Impl`, `ComandaEletronicaControllerTest` para o contrato HTTP), no mesmo pacote, em `src/test/java`.
- **Método**: `deve<Resultado>Quando<Condicao>`, em português — o estilo dos testes novos da base.

```java
deveRetornarVazioQuandoPercentualConfiguradoForZero()
deveLancarErroQuandoComandaNaoPertencerAEmpresa()
deveGerarAcrescimoPeloPercentualDaFormaPagamento()
```

- Sem `@DisplayName` — o nome do método já é a descrição, e os dois divergem na primeira renomeação.
- Sem sufixo `Test`/`Teste` no método.

## Qual nível de teste usar

Prefira sempre o mais barato que responda à pergunta.

| Nível | Quando | Custo |
|---|---|---|
| Unitário com Mockito (`@ExtendWith(MockitoExtension.class)`) | regra de Service, `ServiceApplication`, cálculo, conversão, Mapper | milissegundos, sem Spring |
| `@WebMvcTest` | contrato HTTP do Controller: rota, status, validação, formato de erro, segurança | segundos, só a camada web |
| `@DataJpaTest` + Testcontainers | Repository: método derivado, `@Query`, QueryDSL, `JdbcClient`, migration | segundos, banco real em container |
| `@SpringBootTest` + Testcontainers | fluxo de ponta a ponta que cruza camadas e transação | dezenas de segundos |

`@SpringBootTest` para testar um `if` de Service é desperdício de CI a cada execução.

### Unitário — o padrão para Service

Com injeção por construtor, o teste unitário não precisa de Spring:

```java
@ExtendWith(MockitoExtension.class)
class CalculoComissaoServiceImplTest {

    private static final Long CODIGO_EMPRESA = 1L;

    @Mock
    private CalculoComissaoRepository calculoComissaoRepository;

    @InjectMocks
    private CalculoComissaoServiceImpl calculoComissaoServiceImpl;

    @Test
    void deveCalcularComissaoPeloPercentualDaRegra() {

        PedidoVO pedidoVO = pedidoComValorTotal(new BigDecimal("1000.00"));

        given(this.calculoComissaoRepository.findFirstByCodigoEmpresaAndDataExclusaoIsNull(CODIGO_EMPRESA)).willReturn(Optional.of(regraDezPorCento()));

        BigDecimal comissao = this.calculoComissaoServiceImpl.calcular(CODIGO_EMPRESA, pedidoVO);

        assertThat(comissao).isEqualByComparingTo("100.00");
    }

}
```

- A classe instanciada é a **`Impl`**; as dependências entram como `@Mock` da **interface**. `@InjectMocks` usa o construtor.
- **`MockitoExtension` já é estrita** (equivalente ao `StrictStubs` da base): stub não usado falha o teste. **Nunca `lenient()`** — stub sobrando é teste mal escrito, não configuração a afrouxar.
- Hora atual entra por um `Clock` (`Clock.fixed(...)` no teste) passado no construtor — por isso o Service recebe `Clock`, não chama `now()` (ver `solid.md`).
- **Sem PowerMock**, e sem `mockStatic` para contornar desenho: se o teste precisa mockar estático ou construtor, a dependência deveria ser injetada.

### `@WebMvcTest` — contrato do Controller

```java
@WebMvcTest(ComandaEletronicaControllerImpl.class)
@Import(SecurityConfig.class)
class ComandaEletronicaControllerTest {

    private static final Long CODIGO_EMPRESA = 1L;

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private ComandaEletronicaFacade comandaEletronicaFacade;

    @MockitoBean
    private ComandaEletronicaService comandaEletronicaService;

    @Test
    @WithMockUser(roles = SegurancaConstants.PAPEL_INTEGRACAO)
    void deveResponderBadRequestQuandoNumeroComandaNaoForInformado() {

        assertThat(this.mockMvcTester.post()
                .uri(RotaConstants.ComandaEletronica.BASE + RotaConstants.ComandaEletronica.INTEGRAR)
                .header(HeaderConstants.CODIGO_EMPRESA, CODIGO_EMPRESA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonFixture.ler("comandaeletronica/integrar-sem-numero.json")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.codigo").isNotNull();

        then(this.comandaEletronicaFacade).shouldHaveNoInteractions();
    }

}
```

- **`MockMvcTester`** (AssertJ) no lugar de `MockMvc` + `andExpect`. A rota vem da **constante**, não de texto — renomear a rota não deixa o teste testando outra coisa.
- **`@MockitoBean`** (`org.springframework.test.context.bean.override.mockito`) — `@MockBean` foi removido no Boot 4.
- Teste de Controller verifica **contrato**: status, formato do corpo (de sucesso e de erro), validação e segurança. Regra de negócio se testa no Service.
- Endpoint protegido tem os dois testes: **sem** usuário (401) e **com** (`@WithMockUser` ou `jwt()` do `spring-security-test`). Com o interceptor de empresa (ver `security-and-review.md`), também o teste da empresa que **não** pertence ao usuário, esperando 403 — é o teste que trava vazamento entre tenants.
- Fixture JSON em `src/test/resources/json/<assunto>/`, como a base já faz.

### `@DataJpaTest` + Testcontainers — Repository

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class ComandaEletronicaRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(BancoTesteConstants.IMAGEM_POSTGRESQL);

    @Autowired
    private ComandaEletronicaRepository comandaEletronicaRepository;

    @Test
    @Sql("/sql/comandaeletronica/comandas-duas-empresas.sql")
    void deveListarSomenteComandasDaEmpresaInformada() {

        List<ComandaEletronicaResumoProjection> listaComandaEletronicaResumoProjection =
                this.comandaEletronicaRepository.listarPorFiltro(LongUtil.UM, new FiltroComandaEletronicaDTO(null));

        assertThat(listaComandaEletronicaResumoProjection).hasSize(2).extracting(ComandaEletronicaResumoProjection::numeroComanda).containsExactly(20L, 10L);
    }

}
```

- **`@ServiceConnection`** liga o container ao `DataSource` sozinho — sem `ApplicationContextInitializer` próprio, sem propriedade de URL. `Replace.NONE` impede o Boot de trocar pelo banco embarcado.
- **Liquibase roda na subida da fatia** e cria o schema — o teste valida as migrations a cada build. Se o schema vem de outro artefato (caso do serviço de venda), aponte `spring.liquibase.change-log` do perfil de teste para o changelog dele.
- Imagem do PostgreSQL **da mesma versão de produção**, em constante.
- Cada teste roda numa transação desfeita no final (padrão do `@DataJpaTest`) — dado de um teste não vaza para outro. Massa por `@Sql` ou por seed do Liquibase de teste.
- **Nunca banco compartilhado de desenvolvimento** — teste que depende do estado de um banco que outra pessoa mexe falha por motivo errado e ensina o time a ignorar CI vermelho. A opção `local.database=true` da base é para diagnóstico na máquina, não para o CI.
- Exige Docker (ou Podman) na máquina e no runner de CI.
- **Repository de consulta complexa é o teste que mais paga:** alias errado, parâmetro trocado, tipo de `SUM`, `IN ()` vazio e filtro de tenant esquecido só aparecem em runtime. O teste "duas empresas, lista só a minha" é obrigatório para toda consulta de tabela por empresa.

### `@SpringBootTest` — ponta a ponta

Para o fluxo que precisa de transação real, de vários Services juntos ou da configuração completa. Use o mesmo container (classe base com o `@Container static`, ou `@ImportTestcontainers`) para não subir um PostgreSQL por classe. Integração externa é mockada na fronteira (`@MockitoBean` do `IntercommService`), não com `Fake` em `src/main`.

## Como escrever o teste

- **Um comportamento por teste.** Se o nome precisa de "e", são dois.
- **Três blocos separados por linha em branco**: preparar, executar, verificar.
- **Cenário em método auxiliar `private static`** (`pedidoComValorTotal(...)`, `regraDezPorCento()`) em vez de 15 setters repetidos. É estático porque não depende da instância — e por isso é chamado sem `this.`.
- **Mock só a fronteira** (Repository, `IntercommService`, Publisher). Mockar tudo transforma o teste num espelho da implementação.
- **Ao corrigir um bug, escreva antes o teste que reproduz a falha.**
- **Variável segue a regra de produção**: nome do tipo por extenso (`ClienteServiceImpl clienteServiceImpl`) — nunca `service`, `mock`, `sut`.
- **`@Autowired` em campo é aceito só em classe de teste** — é a forma que o Spring documenta para injetar no teste (`MockMvcTester`, o Repository testado). Em código de produção, continua proibido.
- **Import estático** de `BDDMockito`, `ArgumentMatchers` e `Assertions` (AssertJ).
- **Constante nomeada também no teste** (`LongUtil.UM`, `CODIGO_EMPRESA`), como a base já faz.

### Valor fixo no `given`, `any()` só por exceção

Prefira o valor exato no stub e no `then(...).should()`. `any()` aceita qualquer argumento e esconde a regressão em que a implementação passa a mandar o valor errado — em método com dois `Long` (empresa e PDV), `any()` não percebe os dois trocados de lugar. `any()` fica para o objeto **montado dentro** do fluxo, e mesmo então tipado (`any(NotaFiscalDTO.class)`), conferindo o conteúdo com `ArgumentCaptor` ou `argThat(notaFiscalDTO -> BigDecimalUtil.igual(...))`.

### Assertions — AssertJ

```java
assertThat(obtido).isEqualTo(esperado);
assertThat(valorTotal).isEqualByComparingTo("100.00");                 // BigDecimal: compara valor, não escala
assertThat(listaPedidoDTO).isEmpty();
assertThatThrownBy(() -> this.produtoServiceImpl.salvar(produtoDTOSemDescricao))
        .isInstanceOf(BusinessServiceException.class)
        .extracting(excecao -> ((BusinessServiceException) excecao).getBusinessServiceModel().getCodigo())
        .isEqualTo(DESCRICAO_OBRIGATORIA);
```

Verificar erro confere **o código** do erro, não só o tipo — senão o teste passa por qualquer falha. `assertTrue(a.equals(b))` e `assertTrue(!x)` não se escrevem.

## Migrando um teste de JUnit 4

| JUnit 4 / Boot antigo | JUnit 5 / Boot 4 |
|---|---|
| `@RunWith(MockitoJUnitRunner.StrictStubs.class)` | `@ExtendWith(MockitoExtension.class)` |
| `@RunWith(SpringJUnit4ClassRunner.class)` | nada — `@WebMvcTest`/`@SpringBootTest` já trazem a extensão |
| `org.junit.Test`, `@Before`, `@After` | `org.junit.jupiter.api.Test`, `@BeforeEach`, `@AfterEach` |
| `@Test(expected = X.class)`, `@Rule ExpectedException` | `assertThatThrownBy(...)` |
| `org.junit.Assert.*` | AssertJ `assertThat` |
| `@MockBean` / `@SpyBean` | `@MockitoBean` / `@MockitoSpyBean` |
| `@FixMethodOrder` | nada — teste que depende de ordem está errado; cada um monta o próprio cenário |
| `ApplicationContextInitializer` com `PostgreSQLContainer` | `@Testcontainers` + `@Container` + `@ServiceConnection` |
| `MockMvc` + `andExpect(status().isOk())` | `MockMvcTester` + `assertThat(...).hasStatusOk()` |
| `public class` / `public void` | visibilidade de pacote (`class`, `void`) |

Durante a transição, o `junit-vintage-engine` deixa os testes JUnit 4 rodarem junto — remova-o quando o último migrar.

## Cobertura

JaCoCo (`prepare-agent` + `report`), com a exclusão de cobertura no Sonar restrita ao que não tem lógica (configuração, entidade, DTO). Excluir `repository` inteiro esconde justamente as consultas que mais quebram.

Cubra os casos que doem: cálculo financeiro, integração, regra com muitos ramos, consulta com filtro de tenant. Getter, setter e delegação de uma linha não precisam de teste — cobertura alta obtida testando código trivial dá falsa segurança.
