# Banco de dados e SQL

## Qual caminho de consulta usar

| A consulta é… | Caminho |
|---|---|
| Uma tabela, poucas condições fixas | **Método derivado** do Spring Data (`findFirstByCodigoEmpresaAndNumeroComandaAndDataExclusaoIsNull`) |
| Uma tabela, condição que deixa o nome ilegível, ou `UPDATE`/`DELETE` simples | `@Query` JPQL com text block, método com nome de negócio |
| Filtro dinâmico (condição opcional), `JOIN`, agregação, projeção parcial sobre entidades | **QueryDSL** no `RepositoryImpl` (padrão da base) |
| SQL que só existe em PostgreSQL (`LATERAL`, CTE recursiva, função de janela, procedure), ou relatório sem entidade mapeada | **`JdbcClient`** no `RepositoryImpl`, SQL nativo |
| SQL nativo fixo e curto | `@Query(nativeQuery = true)` com text block |

Regras que valem para os cinco:

- **Todo filtro de tenant explícito** — `codigoEmpresa` (e `codigoPDV`) em toda consulta de tabela que tem a coluna. Ver Repository em `layers-and-naming.md`.
- **Parâmetro sempre nomeado** (`:codigoEmpresa`), nunca posicional (`?1`), e **nunca valor concatenado** na query — é injeção de SQL e impede o banco de reaproveitar o plano.
- **Projeção parcial** quando a tela não precisa da entidade inteira — carregar a entidade com 40 colunas para mostrar 3 é o desperdício mais comum.
- **Nada de N+1**: associação que o fluxo usa vem na consulta (`JOIN FETCH`, `@EntityGraph`, projeção), não de lazy load dentro de laço.

## Spring Data — método derivado e `@Query`

```java
public interface ComandaEletronicaRepository extends CrudRepository<ComandaEletronica, Long>, ComandaEletronicaRepositoryCustom {

    public abstract Optional<ComandaEletronica> findFirstByCodigoEmpresaAndNumeroComandaAndDataExclusaoIsNull(Long codigoEmpresa, Long numeroComanda);

    @Modifying
    @Query("""
            UPDATE ComandaEletronica comandaEletronica
               SET comandaEletronica.dataExclusao = :dataExclusao
             WHERE comandaEletronica.codigoEmpresa = :codigoEmpresa
               AND comandaEletronica.numeroComanda = :numeroComanda
            """)
    public abstract Integer excluir(Long codigoEmpresa, Long numeroComanda, LocalDateTime dataExclusao);

}
```

- Método derivado é inglês (o nome é a query); método com `@Query` é de negócio, em português.
- O nome do parâmetro Java casa com o `:nome` da query porque o projeto compila com `-parameters` (padrão do parent do Spring Boot) — sem isso, `@Param` em cada parâmetro.
- `@Modifying` em `UPDATE`/`DELETE`, chamado de dentro do `ServiceTransactional`. Ele não passa pelo contexto de persistência: entidade já carregada na mesma transação fica desatualizada (`clearAutomatically = true` quando o fluxo relê depois).
- **Paginação**: `Pageable`/`Limit` como parâmetro do método, com teto no servidor (ver "Exaustão de recurso" em `security-and-review.md`). `findAll()` sem limite em tabela que cresce é proibido.
- Retorno parcial: `record` de Projection como tipo de retorno — o Spring Data monta pelo construtor (`SELECT new ...` não é necessário em método derivado; em `@Query` JPQL use o construtor do record ou uma interface projection).

## QueryDSL — consulta dinâmica no `RepositoryImpl`

A base usa QueryDSL com classifier `jakarta` (a biblioteca de repositório compartilhada, a de venda). Em código novo, injete o **`JPAQueryFactory`** — declarado uma vez como `@Bean` sobre o `EntityManager` — em vez de estender `QuerydslRepositorySupport` e sobrescrever `setEntityManager` (padrão da base, que ainda carrega o risco de passar a classe de domínio errada no `super(...)`, como já acontece):

```java
@Configuration(proxyBeanMethods = false)
public class QuerydslConfig {

    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {

        return new JPAQueryFactory(entityManager);
    }

}
```

```java
@RequiredArgsConstructor
public class ComandaEletronicaRepositoryImpl implements ComandaEletronicaRepositoryCustom {

    private final JPAQueryFactory jpaQueryFactory;

    @Override
    public List<ComandaEletronicaResumoProjection> listarPorFiltro(Long codigoEmpresa, FiltroComandaEletronicaDTO filtroComandaEletronicaDTO) {

        QComandaEletronica comandaEletronica = QComandaEletronica.comandaEletronica;

        BooleanBuilder condicoes = new BooleanBuilder()
                .and(comandaEletronica.codigoEmpresa.eq(codigoEmpresa))
                .and(comandaEletronica.dataExclusao.isNull());

        if (ObjectUtil.isNotNull(filtroComandaEletronicaDTO.situacao())) {

            condicoes.and(comandaEletronica.situacao.eq(filtroComandaEletronicaDTO.situacao()));
        }

        return this.jpaQueryFactory
                .select(Projections.constructor(ComandaEletronicaResumoProjection.class,
                        comandaEletronica.numeroComanda,
                        comandaEletronica.situacao,
                        comandaEletronica.valorTotal))
                .from(comandaEletronica)
                .where(condicoes)
                .orderBy(comandaEletronica.numeroComanda.desc())
                .limit(ComandaEletronicaConstants.LIMITE_LISTAGEM)
                .fetch();
    }

}
```

- A condição de tenant entra **primeiro**, fora de qualquer `if` — filtro opcional nunca pode ser o tenant.
- `fetchFirst()` para busca única, embrulhado em `Optional.ofNullable(...)`.
- Filtro opcional com lista: lista vazia não monta a condição (`in()` vazio gera SQL inválido ou "nenhum resultado", conforme a versão — decida pela regra e trate antes).
- **Compatibilidade:** confira que a versão do QueryDSL em uso suporta o Hibernate da versão do Boot (o projeto original anda devagar; o fork mantido é `io.github.openfeign.querydsl`). Se não suportar, a consulta nova vai para `JdbcClient` ou JPQL, em vez de travar o upgrade.
- QueryDSL-SQL (`JPASQLQuery` com classes `QXxxRelational`) existe na base para SQL nativo tipado; em código novo, prefira `JdbcClient`, que não exige gerar e manter as classes relacionais.

## `JdbcClient` — SQL nativo

Para SQL que só existe no PostgreSQL, ou consulta de relatório sem entidade, use o `JdbcClient` (Spring Framework 6.1+), injetado no `RepositoryImpl`:

```java
@RequiredArgsConstructor
public class FechamentoCaixaRepositoryImpl implements FechamentoCaixaRepositoryCustom {

    private final JdbcClient jdbcClient;

    @Override
    public List<FechamentoCaixaProjection> listarPorPeriodo(Long codigoEmpresa, FiltroFechamentoCaixaDTO filtroFechamentoCaixaDTO) {

        StringBuilder sqlNative = new StringBuilder();
        sqlNative.append(" SELECT ");
        sqlNative.append("     caixa.cd_pdv AS codigoPDV, ");
        sqlNative.append("     caixa.dt_fechamento AS dataFechamento, ");
        sqlNative.append("     SUM(movimento.vl_movimento) AS valorTotal ");
        sqlNative.append(" FROM ");
        sqlNative.append("     tb_caixa caixa ");
        sqlNative.append(" INNER JOIN tb_movimentocaixa movimento ON ");
        sqlNative.append("     movimento.cd_caixa = caixa.id_caixa ");
        sqlNative.append("     AND movimento.cd_empresa = caixa.cd_empresa ");
        sqlNative.append(" WHERE ");
        sqlNative.append("     caixa.cd_empresa = :codigoEmpresa ");
        sqlNative.append("     AND caixa.dt_fechamento BETWEEN :dataInicial AND :dataFinal ");

        Map<String, Object> parametros = new HashMap<>();
        parametros.put("codigoEmpresa", codigoEmpresa);
        parametros.put("dataInicial", filtroFechamentoCaixaDTO.dataInicial());
        parametros.put("dataFinal", filtroFechamentoCaixaDTO.dataFinal());

        if (ListUtil.notEmpty(filtroFechamentoCaixaDTO.listaCodigoPDV())) {

            sqlNative.append("     AND caixa.cd_pdv IN (:listaCodigoPDV) ");
            parametros.put("listaCodigoPDV", filtroFechamentoCaixaDTO.listaCodigoPDV());
        }

        sqlNative.append(" GROUP BY ");
        sqlNative.append("     caixa.cd_pdv, ");
        sqlNative.append("     caixa.dt_fechamento ");

        return this.jdbcClient.sql(sqlNative.toString())
                .params(parametros)
                .query(FechamentoCaixaProjection.class)
                .list();
    }

}
```

- **`StringBuilder` para query longa ou montada dinamicamente**, uma coluna/cláusula por `.append`, preservando a indentação do SQL rodado no DBeaver. Query fixa e curta vai numa `String` (ou text block) só.
- **Alias `AS nomeCamelCase`** casando com o componente do `record`/campo da Projection. O PostgreSQL devolve o alias em minúsculo, e o mapeador do `JdbcClient` casa sem diferenciar maiúscula — por isso dois campos que só diferem por maiúscula colidem.
- **`IN (:lista)` com lista vazia** vira `IN ()`, SQL inválido. Trate antes com guard clause, como no exemplo.
- **`COUNT(...)` e `SUM(...)` de inteiro voltam `bigint`/`numeric`** — case o tipo do campo (`Long`, `BigDecimal`) com o que o banco devolve.
- `timestamp` (sem fuso) chega como `LocalDateTime` sem deslocamento quando o campo de destino é `LocalDateTime` — não converta via `java.sql.Timestamp` (ele passa pelo fuso do host).
- `JdbcClient` e JPA na mesma transação compartilham a conexão quando o `DataSource` é o mesmo e o gerenciador de transação é o `JpaTransactionManager` (padrão do Boot com JPA). Se o fluxo grava pela entidade e lê pelo `JdbcClient` na mesma transação, chame `flush()` antes, ou a leitura não vê a escrita pendente.

## Comandos SQL sempre em caixa alta

Em toda query — nativa, JPQL, QueryDSL não se aplica (é Java) —, comando em maiúsculas (`SELECT`, `FROM`, `WHERE`, `INNER JOIN`, `GROUP BY`) e nome de tabela/coluna/alias em minúsculas. Separar visualmente comando de nome facilita leitura e comparação com o script do DBeaver.

**Formatação no DBeaver:** cole o script, selecione e `Ctrl+Shift+F`; configure as palavras reservadas em maiúsculas em `Janela > Preferências > Editores > Editor SQL > Formatação SQL`.

## Ordenação e nome de coluna não aceitam parâmetro

`ORDER BY :campo` não funciona (o banco recebe texto, não coluna), e concatenar o campo que veio do cliente é injeção de SQL. A ordenação vem de um **enum fechado** em que cada constante carrega a expressão fixa:

```java
sqlNative.append(" ORDER BY ").append(ordenacaoListagemClienteEnum.getExpressaoSql());
```

No QueryDSL, o enum carrega o `OrderSpecifier`. Com `Pageable`/`Sort` do Spring Data, a propriedade de ordenação também vem do cliente — valide contra uma lista fechada antes de passar adiante.

## CPF/CNPJ na consulta — igualdade simples, entrada normalizada

CPF e CNPJ são gravados **sem máscara e em maiúsculo** (o CNPJ alfanumérico tem letras). A consulta compara a coluna direto; quem se adapta é o **parâmetro**, normalizado uma vez pelo Util de documento:

```java
// Evite — regra de formato em cada query, e função sobre a coluna impede índice
sqlNative.append(" AND REGEXP_REPLACE(UPPER(cliente.tx_cnpj), '[^0-9A-Z]', '', 'g') = :cnpj ");

// Prefira
sqlNative.append(" AND cliente.tx_cnpj = :cnpj ");
parametros.put("cnpj", CPFCNPJUtil.removerMascara(filtroClienteDTO.cnpj()));
```

Se o Util do projeto ainda não tem o método (ou não trata o CNPJ alfanumérico), acrescente lá, com teste. Documento gravado com máscara se corrige no dado (migration), não com condição defensiva em cada consulta.

## Nomenclatura de estruturas de banco

**Siga o schema em que a tabela vai morar.** Os schemas dos projetos Spring têm a própria convenção, consolidada há anos — tabela nova num schema existente segue o padrão **daquele** schema, não o de outro projeto.

| Estrutura | Regra da base Spring | Exemplo |
|---|---|---|
| Tabela | prefixo `tb_`, nome em minúsculo, palavras juntas, **sufixo do módulo** quando o schema usa | `tb_operadorpdvven`, `tb_administradorapdv`, `tb_cobranca` |
| Chave primária | `id_<tabela sem tb_>` | `id_operadorpdvven` |
| Sequence | `gen_<tabela sem tb_>`, incremento 1 | `gen_operadorpdvven` |
| Constraint de PK | `<tabela>_pk` | `cobranca_pk` |
| Constraint de FK | `<tabela_origem>_<tabela_destino>_fk` | `cobranca_consentimento_fk` |
| Índice | `<tabela>_<colunas>_idx` (único: `_idx_unq`) | `cobranca_cd_empresa_tx_txid_idx_unq` |

- O sufixo de módulo (`ven` venda, `pdv` cadastro do PDV, `mon` monitor, `doc` documento, `con` concentrador) diz a que módulo a tabela pertence, e a entidade Java leva o mesmo sufixo em maiúsculo (`OperadorVEN`). Tabela nova num desses schemas leva o sufixo do módulo dono.
- PK chamada só `id` (PIX) é divergência — `id_<tabela>`.
- Índice numerado (`<tabela>_idx01`) existe na base; em índice novo, nomeie pelas colunas — o nome aparece sozinho em plano de execução e em erro de unicidade, e `_idx01` não diz nada. O PostgreSQL corta identificador acima de 63 caracteres: abrevie a tabela, nunca as colunas.
- Schema de serviço novo, sem convenção herdada, usa o mesmo padrão acima (com `tb_`, `id_`, `gen_`) — é o da maioria dos serviços da equipe.

**Prefixo da coluna pelo tipo do dado:**

| Prefixo | Guarda | Exemplo |
|---|---|---|
| `id_` | chave primária | `id_cobranca` |
| `cd_` | código (FK ou código de domínio) | `cd_empresa`, `cd_pdv`, `cd_ibge` |
| `dt_` | data ou data/hora | `dt_cadastro`, `dt_exclusao` |
| `hr_` | hora | `hr_abertura` |
| `vl_` | valor monetário | `vl_total` |
| `pc_` | percentual | `pc_desconto` |
| `qt_` | quantidade | `qt_item` |
| `nr_` | número não monetário | `nr_comanda`, `nr_sincronizacaoversao` |
| `fl_` | flag/situação de um caractere | `fl_situacao` |
| `tx_` | texto livre ou documento | `tx_nome`, `tx_cnpj` |
| `bl_` | binário | `bl_arquivo` |
| `js_` | JSON | `js_payload` |

O campo Java é sempre o nome **por extenso, em português**: `cd_ibge` → `codigoIBGE`, `vl_total` → `valorTotal`.

**Colunas de controle** que a base usa e a tabela nova repete quando fizer sentido: `dt_cadastro`, `dt_manutencao`, `dt_exclusao` (exclusão lógica) e `nr_sincronizacaoversao` (tabelas sincronizadas com o PDV).

## Enum na coluna

Os enums da base guardam o **código** (`"C"`, `"P"`), e é o código que está na coluna. O mapeamento é um `AttributeConverter` em `repository.domain`:

```java
@Converter(autoApply = true)
public class SituacaoComandaEletronicaConverter implements AttributeConverter<SituacaoComandaEletronicaEnum, String> {

    @Override
    public String convertToDatabaseColumn(SituacaoComandaEletronicaEnum situacaoComandaEletronicaEnum) {

        return ObjectUtil.isNull(situacaoComandaEletronicaEnum) ? null : situacaoComandaEletronicaEnum.getCodigo();
    }

    @Override
    public SituacaoComandaEletronicaEnum convertToEntityAttribute(String codigo) {

        return ObjectUtil.isNull(codigo) ? null : SituacaoComandaEletronicaEnum.getByCodigo(codigo);
    }

}
```

- `@Enumerated(EnumType.STRING)` só quando a coluna guarda o **nome** da constante.
- `EnumType.ORDINAL` nunca: grava a posição, e reordenar o enum troca o significado de todo registro gravado.
- Entidade legada com `String` crua e `XxxEnum.getByCodigo` no Service: ao tocar no campo, troque pelo converter.

## Migration com Liquibase

Os projetos versionam o schema com Liquibase. O que varia por projeto é **onde** o changelog mora (no próprio serviço, numa biblioteca de repositório compartilhada, ou num repositório só de Liquibase). Descubra antes de criar: o serviço de venda exclui o `LiquibaseAutoConfiguration` e recebe o schema de fora.

Estrutura (em `src/main/resources/db/changelog/`):

- changelog master (`db.changelog-master.xml`, ou o par `-development`/`-production` da biblioteca de repositório) — inclui `db.types.xml` e os arquivos de upgrade, em ordem;
- `db.types.xml` — tipos parametrizados (`${numeric.18x0}`, `${varchar.255}`, `${timestamp}`, `${text}`) para PostgreSQL e H2;
- `upgrade/` — migrations incrementais; em projeto com `latest/`, a migration nova nasce em `latest/` e vai para `upgrade/` no empacotamento da versão (siga o fluxo do projeto).

Regras:

- **Nome do arquivo:** `<id>_<comando>_<tabela>.xml`, `<id>` igual ao id do primeiro `changeSet` e `<tabela>` sem `tb_` — `20260804_165035_insert_configuracaovalorpdv.xml`, `20260731_092504_create_table_precoclientepdv.xml`. Projeto que acrescenta versão e módulo ao nome (`<id>-<versão>-<módulo>.xml`) segue o próprio padrão.
- **Id do `changeSet`:** `YYYYMMDD_HHmmSS` do momento da criação; os seguintes no mesmo arquivo usam a hora da própria alteração. **`author`** obrigatório (usuário de quem criou) — o Liquibase identifica o `changeSet` por id + autor + arquivo.
- **`logicalFilePath`**: só o nome do arquivo, sem caminho — mover de `latest/` para `upgrade/` não faz o Liquibase rodar de novo.
- **Um `changeSet` por passo de DDL** (tabela, PK, FK, índice, sequence), cada um com `<preConditions onFail="MARK_RAN">` (`tableExists`, `columnExists`, `indexExists`, `foreignKeyConstraintExists`, `sequenceExists`, dentro de `<not>`). Torna a migration idempotente em banco restaurado ou que recebeu script à mão.
- **A precondição confere exatamente o que o `changeSet` cria.** Para mudança de tipo, tamanho ou `NOT NULL`, ela confere o estado de destino (`sqlCheck` no `information_schema`), não só a existência — senão `MARK_RAN` esconde a divergência.
- **`remarks` em toda tabela e coluna**, em português.
- **Tipos de `db.types.xml`**, nunca o tipo cru.
- **`changeSet` já aplicado nunca é alterado** — o checksum quebra o deploy em todo ambiente onde ele já rodou. Correção é `changeSet` novo. Se for inevitável corrigir um publicado, troque o `id` pela data/hora da correção (mantendo o `logicalFilePath`): a precondição `MARK_RAN` impede a reaplicação onde já rodou.
- **`<include>` ao final da lista**, entre os marcadores do master (`<!-- beg: ... -->` / `<!-- end: ... -->`). A ordem do include é a ordem de execução.
- **Só caracteres LATIN1 no changelog.** Há bases em LATIN1: acento português é válido; travessão (`—`), aspas curvas, reticências (`…`), setas, `≥`/`≤`, marcadores e emoji derrubam o Liquibase (`has no equivalent in encoding "LATIN1"`). Origem mais comum: texto colado de markdown ou de IA. Troque por ASCII (`-`, `"`, `...`, `>=`).
- `UPDATE`/`INSERT` de dado em `<sql>` com `<comment>` dizendo o porquê.

Com Liquibase no projeto, o Hibernate nunca gera schema: `spring.jpa.hibernate.ddl-auto=none` (ou `validate` no perfil de teste, que confere entidade × schema a cada build).
