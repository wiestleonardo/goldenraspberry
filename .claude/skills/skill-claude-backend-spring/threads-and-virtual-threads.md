# Threads e virtual threads (Java 21+)

Referências oficiais — quando esta página e elas divergirem, elas vencem:
- https://docs.spring.io/spring-boot/reference/features/spring-application.html (seção *Virtual threads*)
- https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
- https://docs.spring.io/spring-framework/reference/core/resilience.html — `@ConcurrencyLimit` e `@Retryable`
- https://openjdk.org/jeps/444 — virtual threads
- https://openjdk.org/jeps/491 — fim do pinning por `synchronized` (Java 24+)

## A regra

**Concorrência começa em virtual thread — não em pool de thread de plataforma.** A stack é imperativa/bloqueante, exatamente o cenário para o qual a virtual thread foi desenhada.

A ordem de preferência:

1. **Não criar thread nenhuma.** Com `spring.threads.virtual.enabled=true`, o Tomcat atende **cada requisição numa virtual thread** — o ganho de escala de I/O já vem de graça, sem uma linha de código concorrente. Service e Repository não criam thread.
2. **`@Async`** para disparar trabalho que não precisa segurar a resposta — com virtual threads ligadas, o executor do Spring já usa uma virtual thread por tarefa.
3. **Executor de virtual thread explícito**, quando o trabalho é genuinamente paralelo (fan-out de várias chamadas independentes) e precisa de controle (coletar resultado, limitar concorrência). A mecânica reutilizável fica no pacote `thread`.

**Proibido** (e existe na base): `new Thread(...).start()`, `CompletableFuture.runAsync(...)`/`supplyAsync(...)` **sem executor** (cai no `ForkJoinPool.commonPool()`, que é pequeno, compartilhado com toda a JVM e não propaga contexto), `Executors.newFixedThreadPool(n)` em campo de bean (pool que nunca é desligado e prende o shutdown), e `Executors.newScheduledThreadPool` dentro de `@Scheduled`.

## Ligando

```properties
spring.threads.virtual.enabled=true
spring.main.keep-alive=true
```

Com isso, o Spring Boot passa a usar virtual threads no Tomcat, no `@Async` (`SimpleAsyncTaskExecutor`), no `@Scheduled` (`SimpleAsyncTaskScheduler`) e nos listeners de mensageria que o Boot autoconfigura. `keep-alive` mantém a JVM viva quando não sobra nenhuma thread de plataforma (serviço só de rotina agendada).

**O que muda no `@Scheduled`:** sem pool, duas execuções da mesma rotina podem se sobrepor se uma demorar mais que o intervalo de `fixedRate` — use `fixedDelay` (que espera a anterior terminar) ou trava (ver Job em `layers-and-naming.md`).

## `@Async` — disparar sem esperar

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacaoPixServiceImpl implements NotificacaoPixService {

    private final NotificacaoPixIntercommService notificacaoPixIntercommService;

    @Async
    @Override
    public void notificar(Long codigoEmpresa, NotificacaoPixDTO notificacaoPixDTO) {

        try {

            this.notificacaoPixIntercommService.enviar(codigoEmpresa, notificacaoPixDTO);

        } catch (BusinessServiceException businessServiceException) {

            log.warn("Notificação PIX da empresa {} não entregue; ficará pendente para reenvio", codigoEmpresa);
            ...
        }
    }

}
```

- `@EnableAsync` uma vez, numa `@Configuration`.
- **Método `@Async` chamado da mesma classe roda síncrono** (auto-invocação, sem proxy).
- **Exceção de método `void` assíncrono não chega a ninguém** — vai para o `AsyncUncaughtExceptionHandler` e, sem tratamento, some. O método trata a própria falha (log + pendência para reenvio), como no exemplo. Quem precisa do resultado recebe `CompletableFuture<T>`.
- Trabalho que **não pode se perder** não é `@Async` — se o processo cair, a tarefa em memória cai junto. É fila (Publisher) ou tabela de pendências processada por Job.

## Executor manual — fan-out com limite

### O teto não é a thread, é a conexão

Virtual thread é barata: criar 300 não custa quase nada. Mas se cada tarefa usa uma conexão de banco, **300 tarefas pedem 300 conexões** de um pool de 10 — e o resultado não é lentidão, é o lote morrendo por timeout de pool depois de já ter processado parte. O mesmo vale para o limite de requisições que um parceiro aceita.

Virtual thread remove o limite de **thread**; não remove o de conexão, de memória nem o do parceiro. Onde houver recurso finito atrás da tarefa, **limite a concorrência explicitamente**:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class FechamentoServiceExecutorImpl implements FechamentoServiceExecutor {

    private final FechamentoServiceTransactional fechamentoServiceTransactional;

    private final FechamentoProperties fechamentoProperties;

    @Override
    public Long processarLote(List<Long> listaCodigoEmpresa) {

        Semaphore semaforo = new Semaphore(this.fechamentoProperties.limiteConcorrencia());
        Long quantidadeDocumentosFechados = LongUtil.ZERO;

        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {

            Map<Long, Future<Long>> mapaResultadoPorEmpresa = new LinkedHashMap<>();

            for (Long codigoEmpresa : listaCodigoEmpresa) {

                mapaResultadoPorEmpresa.put(codigoEmpresa, executorService.submit(() -> {

                    semaforo.acquire();

                    try {

                        return new FechamentoExecutor(this.fechamentoServiceTransactional, codigoEmpresa).call();

                    } finally {

                        semaforo.release();
                    }
                }));
            }

            for (Map.Entry<Long, Future<Long>> resultadoEmpresa : mapaResultadoPorEmpresa.entrySet()) {

                try {

                    quantidadeDocumentosFechados = LongUtil.soma(quantidadeDocumentosFechados, resultadoEmpresa.getValue().get());

                } catch (ExecutionException executionException) {

                    log.error("Falha ao processar a empresa {} no lote de fechamento", resultadoEmpresa.getKey(), executionException.getCause());

                } catch (InterruptedException interruptedException) {

                    Thread.currentThread().interrupt();
                    log.error("Lote de fechamento interrompido", interruptedException);
                    throw getBusinessServiceException(PROCESSAMENTO_INTERROMPIDO);
                }
            }
        }

        return quantidadeDocumentosFechados;
    }

}
```

- **`get()` dentro de `try/catch`.** Sem ele, a primeira empresa com erro aborta o laço e descarta o resultado de todas as que deram certo.
- **A falha diz qual empresa falhou** (resultado guardado por `codigoEmpresa`, em `LinkedHashMap` para manter a ordem) — sem isso ninguém sabe qual reprocessar.
- **`InterruptedException` restaura a flag e interrompe o lote** — é o container desligando; engolir faz o shutdown gracioso esperar até o timeout.
- **O limite vem de configuração** (`@ConfigurationProperties`), ajustado por ambiente junto com `spring.datasource.hikari.maximum-pool-size`, **abaixo** do tamanho do pool, com folga para o resto da aplicação.
- O executor em `try-with-resources` **espera** as tarefas terminarem antes de seguir.
- Confira o nome do método de soma no `LongUtil` da versão em uso; se não existir, acrescente-o lá.

### `@ConcurrencyLimit` — o limite declarativo

Para limitar quantas chamadas simultâneas chegam a um recurso (o parceiro externo, uma consulta pesada), o Spring Framework 7 tem `@ConcurrencyLimit` (habilitado com `@EnableResilientMethods`):

```java
@Override
@ConcurrencyLimit(limitString = "${intercomm.cobranca-gateway.limite-concorrencia}")
public RetornoCobrancaGatewayDTO consultar(String identificadorTransacao) {

    return this.cobrancaGatewayClient.consultar(identificadorTransacao);
}
```

Com virtual threads, isso é o que substitui o limite que o tamanho do pool de threads dava "de graça". Use no `IntercommService` de parceiro com cota e no método que dispara consulta cara ao banco. O limite vale **por instância** — com N réplicas, o parceiro recebe N × limite.

## As quatro armadilhas

**1. Pinning.** No Java 21–23, operação bloqueante dentro de `synchronized` (ou de método nativo) prende a virtual thread na thread de plataforma, anulando o ganho — e, com poucas threads de plataforma, pode travar a aplicação. O Java 24+ elimina o caso do `synchronized` (JEP 491); método nativo continua prendendo. **Em Java 21, `synchronized` com I/O dentro é proibido**; use `ReentrantLock`:

```java
private final ReentrantLock reentrantLock = new ReentrantLock();

public void gravar(Pedido pedido) {

    this.reentrantLock.lock();

    try {

        this.pedidoRepository.save(pedido);

    } finally {

        this.reentrantLock.unlock();
    }
}
```

**O lock resolve o pinning, não a concorrência entre réplicas.** Ele serializa só dentro de uma instância; com N réplicas, a exclusão que precisa valer para o serviço inteiro vai para o banco (`SELECT ... FOR UPDATE`, `@Lock(PESSIMISTIC_WRITE)` no Spring Data, ou advisory lock — ver `cloud-native.md`). O `@Synchronized` do Lombok gera `synchronized` — mesma regra.

**2. Nunca faça pool de virtual thread.** Ela é descartável: uma por tarefa. `newFixedThreadPool` de virtual threads reintroduz o limite que ela existe para remover — o limite certo é o semáforo/`@ConcurrencyLimit`.

**3. `ThreadLocal` como cache.** Biblioteca que guarda objeto caro em `ThreadLocal` para reaproveitar passa a alocar um por tarefa, porque a thread nunca se repete. Dado que acompanha o fluxo vai por parâmetro (ou, em Java 25, `ScopedValue`).

**4. Trabalho de CPU.** Virtual thread resolve **espera de I/O**, não cálculo. Processamento pesado de CPU monopoliza a thread de plataforma por baixo; continua num pool de plataforma dimensionado (`ThreadPoolTaskExecutor` com nome próprio), ou sai do request.

## Transação e contexto não atravessam thread

A transação do Spring é amarrada à thread. Um `@Transactional` **não** se propaga para tarefa submetida a executor nem para método `@Async`: cada tarefa que chama um `ServiceTransactional` abre a própria transação, sem rollback conjunto. Escritas que precisam ser atômicas rodam **numa thread só**, dentro do mesmo `@Transactional`; paraleliza-se o que é independente.

Também **não** chegam sozinhos na tarefa:

- **`SecurityContext`** (usuário autenticado) — `SecurityContextHolder` é por thread. Na tarefa, ele está vazio, ou com o contexto de outra requisição se alguém configurou herança.
- **MDC do log** — sem propagação, a linha logada pela tarefa perde o identificador de correlação.
- **Atributos da requisição** (`RequestContextHolder`).

Duas saídas:

1. **Passe o dado explicitamente** — a tarefa recebe por construtor ou parâmetro tudo o que precisa (`codigoEmpresa`, usuário, identificadores), como o `XxxExecutor`. É o caminho preferido.
2. **Propague por `TaskDecorator`** (MDC, `SecurityContext`) no executor do Spring, ou use o suporte de propagação de contexto do Micrometer para tracing. Serve para observabilidade, não substitui passar o tenant explicitamente.

Nunca leia `codigoEmpresa` ou o usuário de contexto **dentro** da tarefa supondo que ele está lá: em teste com uma requisição só, passa; em produção, a tarefa age em nome de outro tenant ou de ninguém.

## Como verificar

- **Diagnóstico de pinning**: o evento `jdk.VirtualThreadPinned` do JFR (Java 21+); `-Djdk.tracePinnedThreads` funciona até o Java 23.
- **Métrica**: o Actuator/Micrometer publica métricas de executor e de virtual threads; vale olhar em serviço que depende delas.
- **Pool de conexão**: com virtual threads, acompanhe `hikaricp.connections.pending` — é ali que o gargalo aparece.
- **Build nativo** exige GraalVM com o mesmo Java; confirme o build depois de introduzir virtual threads.
