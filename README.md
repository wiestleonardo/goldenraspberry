# API Golden Raspberry Awards — intervalos de prêmios dos produtores

Desafio técnico Outsera (back-end Java). A API lê, na inicialização, a lista de indicados e
vencedores da categoria **Pior Filme** do Golden Raspberry Awards (arquivo CSV), grava os dados num
banco H2 em memória e expõe o produtor com **maior** intervalo entre dois prêmios consecutivos e o
que obteve dois prêmios **mais rápido**, no formato definido em
[`challenge/Avaliação - Back-end.pdf`](challenge/Avaliação%20-%20Back-end.pdf).

## Stack

| Item | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 (Spring MVC, Spring Data JPA, Hibernate 7.4) |
| Banco | H2 2.4 embarcado, em memória |
| CSV | Apache Commons CSV 1.14.1 |
| Build | Maven 3.9.15 via Maven Wrapper |
| Testes | JUnit 5 + `@SpringBootTest` + `RestTestClient` (somente integração) |
| Container | Docker multi-stage (`eclipse-temurin:21.0.12.1_1`, fixada por digest) |
| CI | GitHub Actions |

## Pré-requisitos

- **JDK 21** com `JAVA_HOME` apontando para ele. Maven não precisa estar instalado.
- **Docker** — opcional, só para executar em container.
- Nenhum banco ou serviço externo.

## Executar localmente

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Ou empacotando:

```bash
./mvnw package
java -jar target/golden-raspberry-api-1.0.0.jar
```

A aplicação sobe em `http://localhost:8080` e importa o CSV antes de aceitar requisições.

## Executar os testes de integração

```bash
./mvnw verify                   # Windows: mvnw.cmd verify
```

São 27 testes, todos de integração (o PDF exige somente esse tipo). Cada cenário sobe a aplicação
completa (Spring, importação do CSV, H2 e servidor HTTP em porta aleatória) e compara a resposta
HTTP real, em modo JSON estrito, com o resultado **calculado à mão** a partir do arquivo de entrada
— o cálculo está comentado em cada cenário:

| Classe | Cenários |
|---|---|
| [`ProdutorControllerTest`](src/test/java/br/com/outsera/goldenraspberry/api/ProdutorControllerTest.java) | CSV original; contrato JSON; 404 e 405; vitórias consecutivas; três ou mais vitórias; empate no mínimo; empate no máximo; vários empates do mesmo produtor; vários produtores por filme com separadores mistos (`,`, `and`, `, and`, aspas, espaços, nome repetido); filmes não vencedores; anos repetidos (intervalo 0); nenhum intervalo; arquivo só com cabeçalho; linhas fora de ordem; nomes que diferem só em maiúsculas/minúsculas; intervalo único; arquivo Windows (CRLF + BOM); aspas malformadas; `AND`/`And` como separador; linhas duplicadas × filmes distintos no mesmo ano; linhas inválidas |
| [`ImportacaoFilmeJobTest`](src/test/java/br/com/outsera/goldenraspberry/job/ImportacaoFilmeJobTest.java) | arquivo inexistente e cabeçalho incompatível impedem a subida; WARN com linha e motivo para cada linha inválida, aspas malformadas e duplicata; troca do arquivo por um CSV externo (`file:`) |

Os datasets ficam em [`src/test/resources/dataset/`](src/test/resources/dataset).

## Executar com Docker

```bash
docker build -t golden-raspberry-api .
docker run --rm -p 8080:8080 golden-raspberry-api
```

A imagem final tem só o JRE 21 e o jar e roda com usuário sem privilégio. Os testes rodam no
`./mvnw verify` e na CI, não no build da imagem.

## Endpoint

`GET /api/produtores/intervalosPremios`

```bash
curl http://localhost:8080/api/produtores/intervalosPremios
```

Resposta com o CSV original (`200 OK`, `application/json`):

```json
{
  "min": [
    { "producer": "Joel Silver", "interval": 1, "previousWin": 1990, "followingWin": 1991 }
  ],
  "max": [
    { "producer": "Matthew Vaughn", "interval": 13, "previousWin": 2002, "followingWin": 2015 }
  ]
}
```

- `min` e `max` trazem **todos** os empates, ordenados por `producer`, `previousWin` e `followingWin`.
- Sem nenhum produtor com duas vitórias: `{"min":[],"max":[]}`.
- Rota inexistente responde `404` e outro verbo responde `405`, ambos com corpo
  `{"codigo": "...", "mensagem": "..."}`. Stack trace nunca vai na resposta.

Contrato completo em [`specs/001-intervalo-premios-produtores/contracts/intervalos-premios.md`](specs/001-intervalo-premios-produtores/contracts/intervalos-premios.md).

## Como substituir o CSV

O arquivo vem da propriedade `filme.arquivo-csv`. O padrão é `classpath:Movielist.csv`, uma cópia
fiel de `challenge/Movielist.csv` empacotada no jar. Para trocar, sem alterar código:

```bash
java -jar target/golden-raspberry-api-1.0.0.jar --filme.arquivo-csv=file:/caminho/filmes.csv
# ou
FILME_ARQUIVO_CSV=file:/caminho/filmes.csv java -jar target/golden-raspberry-api-1.0.0.jar
# ou, no container
docker run --rm -p 8080:8080 -v /caminho:/dados:ro -e FILME_ARQUIVO_CSV=file:/dados/filmes.csv golden-raspberry-api
```

Formato esperado: UTF-8 (com ou sem BOM), separador `;`, cabeçalho
`year;title;studios;producers;winner`, campos entre aspas aceitos, quebras de linha LF ou CRLF.
Cada linha física é um registro: campo entre aspas com quebra de linha dentro não é suportado (a
linha é descartada com aviso).

Tratamento de dados inválidos (o PDF não define; decidido na fase de clarificação):

- **Linha inválida** (ano ausente/não numérico/menor que 1, título vazio, nenhum produtor, número de
  colunas diferente de 5, `winner` diferente de `yes`/vazio, aspas malformadas como
  `"Crocodile" Dundee II`): a linha é descartada com log `WARN` informando o número da linha e o
  motivo, e a importação continua. A linha nunca é "consertada" (aspas não são removidas em
  silêncio), e uma aspa sem fechar não afeta as linhas seguintes.
- **Duplicata exata** (linha idêntica a outra já lida, após remover espaços das extremidades e
  normalizar a caixa do `winner`): descartada com `WARN` citando a linha original.
- **Problema estrutural** (arquivo inexistente ou ilegível, cabeçalho diferente do esperado): a
  aplicação não sobe e informa o erro.

## Regras de negócio adotadas

- Só filmes com `winner` = `yes` contam, sem diferenciar maiúsculas e minúsculas e ignorando
  espaços; `winner` vazio indica filme não vencedor.
- Os produtores de um filme são separados por `,`, ` and ` e `, and`, com o `and` como palavra
  inteira em qualquer caixa (`AND`, `And`). Cada nome perde apenas os espaços das extremidades;
  nomes que contêm "and" sem espaço em volta, como "Alexandra" ou "Anderson", não são quebrados.
- Nomes são comparados **exatamente**: `Joel Silver` e `joel silver` são produtores distintos. O
  mesmo nome repetido num filme conta uma vez.
- O intervalo é calculado só entre vitórias **consecutivas** do mesmo produtor, em ordem de ano.
  Vitórias em 2000, 2001 e 2010 geram 1 e 9, nunca 10.
- Cada filme vencedor é um prêmio, então dois filmes **distintos** vencedores do mesmo produtor no
  mesmo ano geram intervalo `0`.
- A mesma linha repetida não é outro filme: a duplicata é descartada e não cria prêmio nem
  intervalo `0` artificial.

## Arquitetura resumida

Um módulo Spring Boot, com as camadas e nomes da skill
[`skill-claude-backend-spring`](.claude/skills/skill-claude-backend-spring/SKILL.md):

```text
Inicialização: ImportacaoFilmeJob ─► ImportacaoFilmeFacade ─► LeituraArquivoFilmeService ─► SeparacaoProdutorServiceApplication
                                                          └► FilmeServiceTransactional ─► FilmeRepository ─► H2
Requisição:    ProdutorController ─► IntervaloPremioService ─► FilmeRepository (1 SELECT)
                                                           └► CalculoIntervaloPremioServiceApplication
```

| Pacote (`br.com.outsera.goldenraspberry`) | Responsabilidade |
|---|---|
| `api` / `api.impl` | contrato HTTP (interface) e `@RestController` que só delega |
| `job` | importação no startup (`SmartInitializingSingleton`: roda antes de o Tomcat aceitar conexões) |
| `service.facade` | orquestra a leitura do arquivo (fora de transação) e a gravação (em transação) |
| `service` | leitura/validação do CSV e consulta dos intervalos |
| `service.transactional` | único ponto com `@Transactional(rollbackFor = Exception.class)` |
| `service.application` | cálculo puro: separação de produtores e intervalos |
| `repository` (+ `domain`, `projection`) | Spring Data JPA, entidades `Filme`/`ProdutorFilme`, `VitoriaProdutorProjection` |
| `config` | `@RestControllerAdvice` com formato único de erro |
| `constants`, `dto`, `enumerator`, `exception`, `util`, `vo` | apoio, conforme a skill |

Modelo de dados (`schema.sql`): `tb_filme` (ano, título, estúdios, texto original dos produtores,
`fl_vencedor`) e `tb_produtorfilme` (um registro por produtor individual de cada filme).

## Decisões técnicas e desempenho

- **Uma consulta por requisição.** Um `SELECT` nativo com `JOIN` devolve só `(produtor, ano)` dos
  filmes vencedores, ordenado por produtor e ano, sem N+1. O cálculo percorre a lista uma vez,
  comparando cada vitória com a anterior do mesmo produtor, e depois filtra os empates de mínimo e
  máximo. Complexidade: importação O(N) e consulta O(W log W), dominada pela ordenação no banco
  (N = linhas do CSV, W = pares produtor × filme vencedor); memória O(W).
- **CSV lido linha a linha**, cada linha num parser Commons CSV próprio. Com o arquivo inteiro num
  só parser, uma aspa malformada aborta a importação ou engole as linhas seguintes, e as opções
  tolerantes da biblioteca alteram o dado sem aviso (comportamento verificado e registrado no
  `research.md`, R5).
- **Ordenação final em Java** (produtor, ano anterior, ano seguinte) para a saída ser determinística
  independentemente da collation do banco.
- **Importação antes de abrir a porta HTTP.** Com `ApplicationRunner`, a API ficaria acessível com
  o banco ainda vazio durante a importação.
- **Persistência:** Spring Data JPA (`saveAll` em lote) para gravar e `@Query` nativa curta para
  ler. Alternativas avaliadas e descartadas: `JdbcClient` para tudo, QueryDSL, função de janela
  `LAG`, cálculo sem banco e cache.
- **Sem cache, paralelismo ou camadas extras**: os dados não mudam depois do startup e a consulta já
  é barata.

Detalhes, alternativas e trade-offs estão em
[`specs/001-intervalo-premios-produtores/research.md`](specs/001-intervalo-premios-produtores/research.md).

## Padrões de engenharia adotados

Todo o código segue a skill de time
**[`skill-claude-backend-spring`](.claude/skills/skill-claude-backend-spring/SKILL.md)**,
versionada neste repositório sem alteração. Ela define pacotes e camadas; nomenclatura (domínio em
português, esqueleto técnico em inglês); o par interface + `Impl`; injeção por construtor com
campos `final`; `this.` em todo membro; SQL em caixa alta; `@Transactional` só no
`ServiceTransactional`; logs `@Slf4j` com placeholders; tratamento global de erros; e testes
`deve<Resultado>Quando<Condicao>`. A conformidade é exigida pela
[constitution do projeto](.specify/memory/constitution.md).

Desvios conscientes, todos justificados em
[`research.md` (R9)](specs/001-intervalo-premios-produtores/research.md#r9-desvios-justificados-da-skill):

- `schema.sql` no lugar de Liquibase, porque o banco é recriado em memória a cada subida.
- Sem springdoc, porque o contrato é fixo e se evitam endpoints extras.
- Conversão manual em vez de MapStruct, porque há um único mapeamento.
- Utils mínimos criados localmente, porque a biblioteca compartilhada não existe aqui.
- Job como `SmartInitializingSingleton` em vez de `@Scheduled`.
- Sem Spring Security, porque os dados são públicos e de leitura.
- Sem Actuator: nada no desafio consome o health check, e ele seria um endpoint a mais.

O projeto foi conduzido com o **GitHub Spec Kit**, nas fases constitution → specify → clarify →
plan → tasks → analyze → implement. Os artefatos estão em
[`specs/001-intervalo-premios-produtores/`](specs/001-intervalo-premios-produtores) e em
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Uso de IA

O projeto foi desenvolvido com **Claude Code**, o **GitHub Spec Kit** e a skill
`skill-claude-backend-spring`. As evidências reais estão em **[`docs/ai/`](docs/ai/README.md)**:
prompt inicial, respostas da fase clarify, decisões, problemas encontrados e corrigidos, resultados
das validações e a exportação sanitizada da sessão. A revisão técnica final (achados por severidade e conformidade com a skill) está em
[`docs/revisao-tecnica.md`](docs/revisao-tecnica.md).

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) roda a cada push e pull request: Java 21
(Temurin), cache Maven e `./mvnw -B verify`, que compila e executa os testes de integração. A
pipeline falha se algum teste falhar. Não há etapa de deploy.
