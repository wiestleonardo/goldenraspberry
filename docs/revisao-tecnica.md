# Revisão técnica final

Revisão feita em 2026-10-09 sobre o commit `3e9c712` (branch `001-intervalo-premios-produtores`),
contra o PDF da Outsera, a skill [`skill-claude-backend-spring`](../.claude/skills/skill-claude-backend-spring/SKILL.md)
e a [constitution](../.specify/memory/constitution.md). Formato dos achados conforme
`security-and-review.md` da skill (veredito + severidade por probabilidade × impacto).

## Achados

| ID | Veredito | Severidade | Local | Problema | Situação |
|---|---|---|---|---|---|
| RV-1 | confirmado | Alta | `LeituraArquivoFilmeServiceImpl` | Arquivo UTF-8 com BOM (padrão de CSV exportado pelo Excel) impedia a subida: o BOM grudava no nome da coluna `year` e `record.get("year")` lançava `IllegalArgumentException`. Revelado pelo teste novo `ArquivoWindowsComBom`. | **Corrigido**: BOM removido no fluxo antes do parser (`PushbackReader`); teste verde. |
| RV-2 | confirmado | Alta | `Dockerfile` | A imagem não copiava `lombok.config`; sem ele o Lombok não leva o `@Value` para o construtor e o container falhava na subida (`No qualifying bean of type String`). Encontrado na validação real do container. | **Corrigido**: `lombok.config` copiado no estágio de build; container validado. |
| RV-3 | confirmado | Média | índice git do `mvnw` | No Windows o `mvnw` foi versionado sem bit de execução (`100644`); a CI em Linux falharia com *permission denied*. | **Corrigido**: `git update-index --chmod=+x`; validado com `git archive` + `./mvnw -B verify` em container Linux. |
| RV-4 | confirmado | Média | `ImportacaoFilmeJob` | Com `ApplicationRunner` (desenho inicial), a importação rodaria com o Tomcat já aceitando requisições, e havia uma janela em que a API respondia `{"min":[],"max":[]}`. | **Corrigido** antes do primeiro commit: `SmartInitializingSingleton`; log confirma importação antes de "Tomcat started". |
| RV-5 | confirmado | Baixa | `LeituraArquivoFilmeServiceImpl` | Cada linha separava os produtores e interpretava o `winner` duas vezes (validação e montagem). | **Corrigido**: validação sobre o `FilmeVO` já montado. |
| RV-6 | confirmado | Baixa | `ProdutorControllerTest` | `"Allow"` e `405` literais no teste. | **Corrigido**: `HttpHeaders.ALLOW`, `HttpStatus.METHOD_NOT_ALLOWED`. |
| RV-7 | confirmado | Baixa | `FilmeConstants` | Caractere BOM invisível inserido no código-fonte durante uma edição. | **Corrigido**: constante `0xFEFF`; varredura sem ocorrências. |
| RV-8 | precisa-validar | — | regra de negócio | Linha duplicada no CSV (mesmo ano, título e produtor) conta como dois filmes e pode gerar intervalo 0. O arquivo não tem identificador de filme; deduplicar seria regra não pedida. | Mantido e documentado no README (premissa). |
| RV-9 | rejeitado | — | `LeituraArquivoFilmeServiceImpl` (~200 linhas) | Acima das ~150 linhas sugeridas pela skill. | Uma responsabilidade só (ler e validar o arquivo); métodos privados curtos. Dividir seria fragmentação (`solid.md`). |
| RV-10 | rejeitado | — | `identificarVencedor` devolve `null` | `null` em retorno. | Método privado; o VO incompleto é rejeitado na mesma classe e nunca atravessa camada (comentado no código). |
| RV-11 | confirmado | Baixa | `LeituraArquivoFilmeServiceImpl.abrirLeitorSemMarcaOrdemByte` | Se a primeira leitura lançar `IOException`, o stream não é fechado antes da exceção. | Aceito: a falha já interrompe a inicialização; o processo termina. |

## Verificação do PDF

| Requisito | Evidência |
|---|---|
| Ler o CSV e inserir em base ao iniciar | `ImportacaoFilmeJob` → `ImportacaoFilmeFacade`; log "206 filmes gravados" |
| Maior intervalo e dois prêmios mais rápido no formato da pág. 2 | `GET /api/produtores/intervalosPremios`; teste estrito com o CSV original |
| Richardson nível 2 | recurso por URI, `GET` sem efeito colateral, 200/404/405, JSON |
| Somente testes de integração | 23 testes, nenhum unitário; todos sobem o contexto real |
| Banco em memória embarcado, sem instalação | H2 `mem`, nome único por contexto |
| README com execução e testes | `README.md` |
| Repositório git | histórico incremental de commits |
| Registro de interações com IA | `docs/ai/` |
| Precisão com outros datasets | 15 datasets alternativos com resultado esperado calculado à mão |

## Conformidade com a skill

| Regra | Situação |
|---|---|
| Estrutura de pacotes (`api/impl`, `config`, `constants`, `dto`, `enumerator`, `exception`, `job`, `repository/domain/projection`, `service/impl/facade/transactional/application`, `util`, `vo`) | ✅ só os pacotes com responsabilidade real |
| Fluxo de injeção (Job → Facade → Service/ServiceTransactional → Repository; Controller → Service) | ✅ sem atalho nem injeção para cima |
| Par interface + `Impl` em Controller, Facade, Service, ServiceTransactional, ServiceApplication | ✅ |
| Controller: contrato na interface, `Impl` `@RestController` só delega, log de entrada, `ResponseEntity`, rota de `RotaConstants` | ✅ |
| Injeção por construtor (`@RequiredArgsConstructor`), campos `private final`, `@Value` via `lombok.config` | ✅ nenhum `@Autowired` em produção |
| `this.` em todo acesso a membro | ✅ varredura sem ocorrência faltante |
| `public abstract` em método de interface | ✅ |
| Linha em branco no início de cada bloco; campos injetados separados | ✅ |
| Ordem dos membros (constantes, injetados, públicos, privados) | ✅ |
| Constantes em classe `final` com construtor privado; regex em `RegexConstants` | ✅ |
| Domínio em português, esqueleto técnico em inglês; nomes por extenso (`listaFilmeVO`, `csvRecord`) | ✅ |
| Tipos: `Long` para ano/intervalo, `Boolean` wrapper, coleções pela interface | ✅ |
| Comparação e cálculo pelo Util (`ObjectUtil.isEquals`, `LongUtil.igual/menos`) | ✅ Utils mínimos criados localmente (biblioteca inexistente) |
| `@Transactional(rollbackFor = Exception.class)` do Spring, só no método do `ServiceTransactional`; sem I/O externo dentro | ✅ leitura do arquivo fica na Facade, fora da transação |
| `spring.jpa.open-in-view=false`, sem `enable_lazy_load_no_trans`, associação `LAZY`, `saveAll` com batch | ✅ |
| SQL em caixa alta, alias casando com o `record` da Projection, sem concatenação | ✅ |
| Nomenclatura de banco (`tb_`, `id_`, `cd_`, `nr_`, `tx_`, `fl_`, `gen_`, `_pk`, `_fk`) | ✅ |
| Entidade `@Getter @Setter`, `Serializable`, equals/hashCode só pelo id, `allocationSize = 1` | ✅ |
| DTO `record`, singular, prefixo `Retorno*DTO`, `@Builder` com mais de 3 campos | ✅ |
| `@Slf4j`, placeholders, sem dado sensível, exceção como último argumento | ✅ |
| `@RestControllerAdvice` estendendo `ResponseEntityExceptionHandler`, `handleExceptionInternal` com `@Override`, corpo único, sem stack trace | ✅ |
| Exceção de negócio `BusinessServiceException` + `ErroEnum` (código, chave, status) + `messages.properties` | ✅ |
| Virtual threads, shutdown gracioso, Actuator só `health` com probes | ✅ |
| Testes: JUnit 5, `deve<Resultado>Quando<Condicao>`, preparar/executar/verificar, surefire em UTC | ✅ (só integração por exigência do PDF, que prevalece sobre a pirâmide da skill) |
| Desvios (Liquibase, springdoc, MapStruct, Job, Security) | ⚠️ explícitos e justificados em `research.md` R9 |

## Validações executadas

| Validação | Resultado |
|---|---|
| `mvnw.cmd -B clean verify` (Windows, JDK 21.0.9) | 23 testes, 0 falhas, `BUILD SUCCESS` |
| `git archive HEAD` → container `eclipse-temurin:21-jdk-noble` → `./mvnw -B verify` (simula o job da CI) | 23 testes, 0 falhas, `BUILD SUCCESS` |
| `docker build` | sucesso |
| `docker run` → `id` / `/actuator/health` / endpoint / rota inexistente | uid 999 (não root) / `UP` / `200 application/json` com o JSON esperado / `404` |
| `docker run` com volume e `FILME_ARQUIVO_CSV=file:/dados/empate-maximo.csv` | resultado do dataset alternativo |
| `docker stop` | exit 143 (SIGTERM tratado), pool encerrado — shutdown gracioso |
| Latência local do endpoint (CSV original) | 1ª chamada 0,22 s; seguintes ~0,011 s |
| GitHub Actions no GitHub | **não executada** — repositório não publicado (push não autorizado) |
