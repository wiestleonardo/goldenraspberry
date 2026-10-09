# Cloud native — performance e escala na implementação

Toda implementação parte da pergunta: **como isso se comporta com N réplicas, num container com memória limitada, podendo ser morto e recriado a qualquer momento?** É critério de desenho, junto com a regra de negócio.

Parte dos serviços desta base roda **instalada no cliente** (PDV, periféricos, agente — uma instância por máquina, com log em arquivo e configuração local) e parte em **nuvem** (integrações, PIX, serviços chamados por Cloud Run). As cinco perguntas valem para o segundo grupo sempre, e para o primeiro sempre que o código puder ir para a nuvem — o que costuma acontecer.

Quando uma limitação de infraestrutura aparecer, **sugira a alternativa gerenciada de GCP ou AWS** em vez de resolver na mão. Sugerir, não impor: a decisão é de arquitetura e custo, do time.

## As cinco perguntas

**1. Isso guarda estado na instância?**

Estado em memória (`ServiceSingleton` com mapa, `@Cacheable` local, campo mutável em bean) funciona com uma réplica e passa a divergir com duas: cada instância carrega, invalida e serve o seu. Aceitável para **dado praticamente imutável** (parâmetro, de-para fiscal que muda em release), com TTL. Deixa de ser no momento em que o dado muda em runtime e a resposta errada importa. Aí:
- **GCP**: Memorystore for Redis · **AWS**: ElastiCache for Redis/Valkey
- No Spring: `spring-boot-starter-data-redis` como provedor do mesmo `@Cacheable` — o código de negócio não muda.

**2. Isso dispara sozinho?**

`@Scheduled` roda **em toda réplica**. Com 3 réplicas, o fechamento roda 3 vezes. É a falha de escala mais comum e a mais silenciosa, porque em desenvolvimento só existe uma instância.

Saídas, da mais simples à mais robusta:
- **ShedLock** (`@SchedulerLock` + `JdbcTemplateLockProvider` com a tabela `shedlock` criada por migration): a réplica que não obtém a trava pula a execução. É o padrão de fato no ecossistema Spring e não exige infraestrutura nova. `lockAtMostFor` maior que a duração esperada, para a trava não expirar no meio; `lockAtLeastFor` para réplicas com relógio ligeiramente diferente não rodarem em sequência.
- **Advisory lock do PostgreSQL** no início da rotina — `pg_try_advisory_xact_lock(:chave)` dentro da transação (liberado no commit/rollback; funciona atrás de proxy de conexão), nunca `pg_try_advisory_lock` de sessão atrás de PgBouncer/RDS Proxy em modo transação.
- Agendador externo chamando um endpoint: **GCP** Cloud Scheduler · **AWS** EventBridge Scheduler.
- Fila com consumidor competindo: **GCP** Pub/Sub · **AWS** SQS.

**3. Quantas conexões de banco isso abre?**

Pool é por **instância**; o limite é do **banco**. `maximum-pool-size=20` com 10 réplicas são 200 conexões. Dimensione `réplicas_no_pico × maximum-pool-size < limite_do_banco`, com folga para migration e acesso administrativo.

Com **virtual threads**, isso fica mais crítico: o Tomcat deixa de limitar requisições simultâneas pelo tamanho do pool de threads, e milhares de requisições disputam as mesmas 10 conexões. `connection-timeout` curto (falhar rápido), `@ConcurrencyLimit` na operação pesada e limite de concorrência no lote paralelo abaixo do pool (ver `threads-and-virtual-threads.md`). Quando a conta não fecha:
- **GCP**: Cloud SQL (com Cloud SQL Auth Proxy) · **AWS**: RDS/Aurora com **RDS Proxy**.

**4. Quanto tempo isso segura um recurso?**

Transação longa segura conexão e lock, e piora com mais réplicas disputando o mesmo pool.
- **N+1**: consulta dentro de laço, ou lazy load disparado em laço (que o `enable_lazy_load_no_trans` esconde).
- **I/O externo dentro de `@Transactional`** — já proibido em `code-style.md`; em nuvem a latência de rede varia e a conexão fica presa junto.
- **Processamento pesado no request**: passou de alguns segundos, é trabalho assíncrono — fila ou tabela de pendência processada por Job, e responda.

**5. Isso sobrevive a ser morto no meio?**

- **Log em stdout**, estruturado (`logging.structured.format.console`), nunca em arquivo dentro do container. Coleta por **Cloud Logging** (GCP) ou **CloudWatch Logs** (AWS).
- **Health com Actuator**, liveness e readiness separados (`management.endpoint.health.probes.enabled=true`, `/actuator/health/liveness` e `/actuator/health/readiness`):
  - **readiness** — "posso receber tráfego?": inclui banco e dependências. Falhou, o orquestrador para de mandar requisição.
  - **liveness** — "o processo travou?": **nunca** inclui o banco. Liveness que checa o banco reinicia todas as réplicas em cascata numa queda do banco, e elas sobem disputando conexão justo quando ele volta.
- **Shutdown gracioso** (`server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase`) com valor **menor** que o prazo que o orquestrador espera antes de matar o processo (`terminationGracePeriodSeconds`, 30 s por padrão no Kubernetes; o equivalente no Cloud Run/ECS).
- **Segredo fora dos arquivos** — de **Secret Manager** (GCP) ou **Secrets Manager**/Parameter Store (AWS), por variável de ambiente ou pelo `spring.config.import` do provedor.
- **Idempotência**: se a requisição ou mensagem pode ser reentregue depois de o processo morrer no meio, rodar duas vezes não dobra o resultado — integração de venda, cobrança PIX, lançamento financeiro.
- **`@Async` e fila em memória somem com o processo** — o que não pode se perder vai para fila ou tabela.

## Build nativo e custo de instância

Build nativo muda o comportamento em ambiente que escala por demanda: partida em dezenas de milissegundos e memória muito menor significam escalar mais rápido no pico e caber em instância menor — o que importa em **Cloud Run** (GCP) e **ECS Fargate**/**App Runner** (AWS), onde se paga por memória alocada e o scale-to-zero cobra a partida em latência. Sem nativo, o **CDS/AOT cache** da JVM (Java 24+) reduz parte da partida sem as restrições do nativo. Os cuidados do nativo estão em `stack-and-build.md`.

## Quando sugerir serviço gerenciado

| Você está prestes a escrever… | Sugira GCP | Sugira AWS |
|---|---|---|
| cache compartilhado entre réplicas | Memorystore | ElastiCache |
| controle de quem executa a rotina agendada | Cloud Scheduler | EventBridge Scheduler |
| fila, retry e dead-letter na mão | Pub/Sub | SQS + SNS/EventBridge |
| multiplexação de conexão de banco | Cloud SQL Auth Proxy | RDS Proxy |
| guardar arquivo gerado (XML, PDF, relatório) | Cloud Storage | S3 |
| rotação e leitura de segredo e certificado | Secret Manager | Secrets Manager |
| rastrear uma requisição entre serviços | Cloud Trace (via OpenTelemetry) | X-Ray (via OpenTelemetry) |

Ao sugerir, diga **qual problema concreto** aquilo resolve no caso e o que muda no código (starter, propriedade, ponto de falha novo). Continuar no que já existe é resposta legítima quando o volume não justifica.
