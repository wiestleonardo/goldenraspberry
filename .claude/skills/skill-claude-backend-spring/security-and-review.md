# Segurança e revisão de código

Duas coisas neste arquivo: as regras de segurança inegociáveis, e o checklist de revisão de código Java. O que já está em outro arquivo aparece só como ponteiro.

## Segurança — regras inegociáveis

### Segredo nunca no código

Chave criptográfica, senha (de banco, de keystore, de usuário técnico), segredo de JWT, token e API key vêm de variável de ambiente alimentada por Secret Manager/Vault — **nunca** em literal Java, constante, `application*.properties`, `pom.xml`, `Dockerfile` ou arquivo em `src/main/resources`.

```java
// PROIBIDO — existe na base nas três formas
private static final String SEGREDO_JWT = "...";
.password("{noop}" + SegurancaConstants.SENHA_USUARIO_INTEGRACAO)
server.ssl.key-store-password=...      (em application-prod.properties)

// CORRETO
@ConfigurationProperties(prefix = "seguranca.jwt")
public record JwtProperties(@NotBlank String segredo, @NotNull Duration validade) {
}
// seguranca.jwt.segredo=${JWT_SEGREDO}
```

- **Certificado e keystore** (`.p12`, `.pfx`, `.jks`) não ficam no repositório: são montados no container a partir do Secret Manager e referenciados por **SSL bundle** (`spring.ssl.bundle.jks.<nome>.keystore.location=${...}`), com a senha também do ambiente.
- **Credencial de repositório Maven** vem do `settings.xml`, nunca do `pom.xml`.
- **Segredo que já foi commitado está comprometido** mesmo depois de removido — o histórico guarda. Rotacione, não basta apagar a linha. Ao encontrar um, avise o time em vez de só corrigir o arquivo.

### Senha: hash forte, nunca texto puro

| Não use | Use |
|---|---|
| `{noop}`, `PasswordEncoder` que compara texto puro | `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (bcrypt por padrão, aceita migrar de algoritmo) |
| MD5, SHA1, SHA-256 puro para senha | bcrypt (custo 12+) ou Argon2id |
| Senha gravada e consultada em texto (`findBy...AndSenha...`) | busca pelo login, `passwordEncoder.matches(senhaInformada, hashGravado)` |
| DES, 3DES, ECB, IV fixo | AES-256-GCM com IV aleatório por `SecureRandom` |
| Base64, XOR | não são criptografia |

Senha de operador que hoje está em texto puro na tabela: a migração (coluna de hash, conversão no primeiro login, remoção da coluna antiga) é tarefa própria, combinada com o time — mas código novo **nunca** consulta senha por igualdade no banco.

### Spring Security — `SecurityFilterChain` com DSL lambda

```java
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessionManagement -> sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeHttpRequests -> authorizeHttpRequests
                        .requestMatchers(HttpMethod.POST, RotaConstants.Ativacao.BASE + RotaConstants.Ativacao.CONFIRMAR).permitAll()
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2ResourceServer -> oauth2ResourceServer.jwt(Customizer.withDefaults()))
                .build();
    }

}
```

- **Rota pública de constante** (ver Controller em `layers-and-naming.md`), e lista curta: tudo o que não está nela é autenticado (`anyRequest().authenticated()`), nunca o contrário.
- `csrf.disable()` só em API **stateless** com token no header — nunca com sessão/cookie.
- **Nada de `web.ignoring()`** para rota de API: rota ignorada não passa por nenhum filtro de segurança (nem cabeçalhos). Para liberar, `permitAll()`.
- `headers.frameOptions().disable()` só com motivo (a tela que precisa ser embutida), e restrito a ela.

### JWT — resource server, não filtro próprio

Validação de token é do **resource server do Spring Security** (`spring.security.oauth2.resourceserver.jwt.issuer-uri`, ou a chave pública/`jwk-set-uri`), não de um `OncePerRequestFilter` escrito à mão com jjwt. O filtro próprio da base valida só algumas URIs, pula a validação quando o header contém `"Basic "` e monta a autenticação sem papéis — três furos que o resource server não tem.

- **Expiração curta obrigatória.** Token sem `exp`, ou de um ano ("manter logado"), é credencial permanente: "manter logado" é refresh token com rotação, não token de acesso longo.
- **Algoritmo forte e assinatura sempre verificada**; segredo HMAC (quando inevitável) com no mínimo 256 bits, vindo do ambiente.
- **Confira emissor e público** (`issuer-uri` e `spring.security.oauth2.resourceserver.jwt.audiences`): assinatura válida só prova quem tem a chave — sem `iss`/`aud`, token emitido para outro sistema do mesmo provedor é aceito aqui.
- **Papéis no token** convertidos em autoridades (`JwtAuthenticationConverter`) — autenticação sem autoridade não permite autorização por papel.

### Endpoint que devolve dado pessoal exige autenticação

Dado pessoal: nome, CPF/CNPJ, RG, e-mail, telefone, data de nascimento, endereço, foto, biometria.

- **Dado pessoal nunca em path nem em query string.** A URL vai para o log de acesso do servidor, do proxy e do gateway, e para o histórico do navegador. Filtro por documento vai no corpo de um `POST`, mesmo sendo consulta.
- Endpoint que precisa ser público (verificar se um cadastro existe): devolve só o booleano, tem rate limiting, e responde no mesmo tempo nos dois casos.
- Devolva **só o necessário** — DTO de resumo, não a entidade inteira.

### Rate limiting em endpoint público

Login, ativação, reset de senha, consulta por documento, envio de e-mail/SMS, upload e qualquer `permitAll()` precisam de limite — na aplicação ou na borda (API gateway, load balancer, Cloud Armor/WAF). Se ficar na borda, **registre no projeto**, senão toda revisão reabre o achado.

### Dado que vem do cliente é validado no servidor

Header, parâmetro, campo escondido e valor em token não assinado são entrada do usuário, mesmo quando "só o nosso PDV manda". O caso mais comum desta base — `codigoEmpresa` (e `codigoPDV`) por header — é conferido **uma vez**, num `HandlerInterceptor`, antes de qualquer Controller:

```java
@Component
@RequiredArgsConstructor
public class EmpresaAutorizadaInterceptor implements HandlerInterceptor {

    private final EmpresaUsuarioService empresaUsuarioService;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler) throws IOException {

        Long codigoEmpresa = LongUtil.toLong(httpServletRequest.getHeader(HeaderConstants.CODIGO_EMPRESA));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (ObjectUtil.isNull(codigoEmpresa) || ObjectUtil.isNull(authentication) || !authentication.isAuthenticated()) {

            return true;
        }

        if (BooleanUtil.isTrue(this.empresaUsuarioService.pertenceAoUsuario(authentication.getName(), codigoEmpresa))) {

            return true;
        }

        httpServletResponse.sendError(HttpStatus.FORBIDDEN.value());

        return false;
    }

}
```

Registrado num `WebMvcConfigurer` (`addInterceptors`) para as rotas da API.

- O interceptor roda **depois** do `SecurityFilterChain`: o usuário já é conhecido quando a empresa é conferida.
- Requisição anônima passa porque rota protegida já foi barrada antes. Consequência: rota **pública** não pode devolver nem alterar nada de uma empresa só porque o header a nomeou.
- Empresa que não pertence ao usuário: 403 (nega o acesso à empresa). Registro de outra empresa: 404 (não confirma existência).
- Em contrato novo, derive a empresa da identidade (claim do token) e dispense o header.
- Serviço instalado no cliente (PDV local, agente) tem modelo de confiança diferente — a máquina é do cliente —, mas a regra continua: o servidor de nuvem que recebe dele confere a empresa contra a credencial, não contra o header.

### Campo de controle não vem do corpo (atribuição em massa)

O corpo só preenche o que o cliente tem direito de informar. **Id, empresa, situação, usuário responsável e valor calculado** são definidos pelo servidor.

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PedidoMapper {

    @Mapping(target = "idPedido", ignore = true)
    @Mapping(target = "codigoEmpresa", ignore = true)
    @Mapping(target = "valorTotal", ignore = true)
    Pedido map(PedidoDTO pedidoDTO);

}
```

- O Service define esses campos a partir do que o servidor sabe — a empresa conferida, o id da sequence, o total recalculado.
- Melhor ainda: o DTO de entrada nem declara esses campos.
- Na alteração, o registro é carregado pelo par (empresa, id) e só os campos editáveis são copiados — nunca `save` de uma entidade montada inteira a partir do corpo (o `save` do Spring Data faz `merge` e sobrescreve tudo).

### Nunca expor infraestrutura interna

Fora de resposta, header e mensagem de erro: URL interna, IP, stack trace, versão de framework.

```properties
server.error.include-stacktrace=never
server.error.include-message=never
management.endpoints.web.exposure.include=health,info,prometheus
```

O handler global (ver `layers-and-naming.md`) devolve mensagem tratada; o stack trace fica no log. Actuator exposto só com o necessário e, fora `health`, protegido ou em porta de gerência separada (`management.server.port`). Swagger desligado em produção quando a API não é pública.

### CORS, upload, cliente HTTP

- **CORS**: origem explícita, de configuração (`CorsConfigurationSource` com `setAllowedOrigins` vindo de propriedade). `applyPermitDefaultValues()` (todas as origens) e `*` com credenciais são proibidos.
- **Upload**: valide extensão, tamanho (`spring.servlet.multipart.max-file-size`) e **conteúdo** — `Content-Type` vem do cliente. Nunca salve com o nome enviado.
- **Cliente HTTP que recebe URL de fora** (callback de parceiro, URL de cadastro): é vetor de SSRF. Restrinja o destino (lista de hosts, bloqueio de rede interna — o Boot 4.1 trouxe filtro de endereço para os clientes HTTP) antes de chamar.
- **mTLS com parceiro** (PSP do PIX): SSL bundle, cliente com `client-auth=need`, e o certificado do cliente validado contra o cadastro — não só "tem certificado".

### Isolamento entre tenants

Num serviço multi-tenant — e aqui quase tudo é, por `codigoEmpresa` e `codigoPDV` — **a falha mais provável não é invasão de fora, é vazamento de lado**: a empresa A enxergando dado da B. Não dispara alarme e costuma ser descoberto pelo cliente.

- **Todo `SELECT`, `UPDATE` e `DELETE` em tabela com `cd_empresa` filtra o tenant?** Uma cláusula a menos já vaza; em `UPDATE`/`DELETE`, altera dado alheio. Exceção: tabela global, sem a coluna.
- **`findById`, `findAll`, `deleteById` herdados do `CrudRepository`** não filtram empresa — em tabela por empresa, não os use a partir de id vindo do cliente.
- **O tenant vem de onde?** Do corpo ou de header não conferido, o cliente escolhe. O confiável é o derivado da autenticação, ou o header conferido pelo interceptor.
- **Cache e Singleton segmentados por tenant?** `@Cacheable` com chave sem a empresa, ou `Map` de Singleton sem a empresa na chave, serve dado de uma empresa para outra.
- **Mensagem de erro e log não revelam dado alheio** ("documento já cadastrado" conta que existe em outra empresa).

### Exaustão de recurso

- Consulta sem limite nem paginação em tabela que cresce (`findAll()`).
- Paginação sem teto (`size=1000000`) — `spring.data.web.pageable.max-page-size` e teto no próprio método.
- Upload sem limite, arquivo compactado que expande.
- Regex com retrocesso catastrófico sobre texto do usuário.
- Laço cujo número de iterações vem da requisição.
- Com virtual threads, **sem limite natural de threads**: requisição que dispara fan-out sem `@ConcurrencyLimit`/semáforo multiplica conexões.

### Cadeia de suprimentos

- Dependência com CVE conhecido — verificação no build (OWASP Dependency-Check, Dependabot/Renovate, scanner do Artifactory), não manual.
- Versão flutuante (`LATEST`, faixa aberta) torna o build não reproduzível.
- Dependência nova entra por revisão: quem mantém, o que puxa junto, se o BOM do Boot já gerencia.

### Dado pessoal e credencial nunca vão para o log

Regra completa em "Logs", `code-style.md`. Logue o **identificador**, não o conteúdo — e nunca token, header `Authorization` ou corpo de requisição de parceiro (log do cliente HTTP em nível de corpo é proibido em produção).

```java
// PROIBIDO — existe na base
log.info("==> Token gerado para clientId {}: {}", clientId, oAuthTokenDTO);

// CORRETO
log.info("Token do PSP renovado; expira em {}", oAuthTokenDTO.dataExpiracao());
```

## Checklist de revisão de código (Java)

### Bugs latentes

```java
cliente.getEndereco().getCidade();              // getEndereco() pode ser nulo
for (Item item : lista) { lista.remove(item); } // ConcurrentModificationException — removeIf()
if (valor == 0.1) { ... }                       // ponto flutuante — BigDecimal
Long a = 200L, b = 200L; if (a == b) { ... }    // false — LongUtil.igual
int total = quantidade * valorUnitario;         // estouro silencioso — Util
String.format("%.2f", valor);                   // Locale do host
LocalDate hoje = LocalDate.now();               // fuso do host (UTC em container) — Clock
Long cnpj = clienteDTO.getCnpj();               // documento é String
if (situacao == "ATIVO") { ... }                // String com == — e situação é enum
new BigDecimal(0.1);                            // construtor de double — BigDecimal.valueOf ou String
valorTotal.divide(quantidade);                  // dízima lança ArithmeticException — escala e arredondamento
```

Verifique também:
- `Boolean` de entidade/DTO direto em `if` (unboxing de `null`).
- `Optional.get()` sem verificação; `Optional` como parâmetro ou campo.
- `equals` sem `hashCode`; entidade com `@Data` e associação.
- Recurso fora de `try-with-resources`.
- `InterruptedException` sem `Thread.currentThread().interrupt()`.
- **Anotação de proxy chamada pela própria classe** — `@Transactional`, `@Async`, `@Retryable`, `@Cacheable` via `this.` não fazem nada.
- **`@Transactional` do pacote errado** (`jakarta.transaction`) ou sem `rollbackFor`.
- **I/O externo dentro de transação.**
- **Campo mutável em bean singleton** (`private Boolean primeiraExecucao`, `private List<...> cache = new ArrayList<>()`) — todo bean é compartilhado entre requisições.
- **Campo `static` não-`final`** fora de Singleton.
- `@Value` em campo `static` (fica nulo — o Spring não injeta em estático).

### Ausência e vazio

`null` não atravessa camada: busca única devolve `Optional`, lista devolve lista vazia, ausência que é erro vira erro de negócio. Casos de borda em toda revisão:

- Lista vazia onde o código supõe um elemento (`lista.get(0)`, `getFirst()`).
- Id nulo chegando num método de busca (o Spring Data lança `IllegalArgumentException` em `findById(null)`).
- String vazia tratada (ou não) como nula.
- Divisão com denominador vindo de entrada.
- Data nula em `isBefore`/`isAfter`.
- Página negativa ou tamanho zero.

### Clareza

- Nome que não comunica (`dados`, `obj`, `temp`, `x`, `service`).
- Negativa dupla; aninhamento acima de três níveis; `else` depois de `return`.
- Comentário óbvio ou código comentado.
- Método fazendo mais de uma coisa (SRP, `solid.md`); construtor com dependências demais.

### Erro

```java
// EVITE
catch (Exception e) { throw new RuntimeException(e); }
catch (Exception e) { if (e instanceof PspApiException) { ... } throw new BusinessServiceException(...); }

// PREFIRA
catch (HttpClientErrorException httpClientErrorException) {

    throw getBusinessServiceException(COBRANCA_RECUSADA_GATEWAY);
}
```

Também é achado:
- **Logar e relançar** a mesma exceção — o handler global loga de novo.
- `e.printStackTrace()`, `log.error(e.getMessage())`, erro logado em `info`.
- `catch` que troca a exceção e perde a causa original sem registrá-la.
- `catch (Exception)` dentro de método transacional — engole o rollback.

### Performance

Já cobertos em `cloud-native.md` (N+1, consulta em laço, transação larga, processamento pesado no request). Some:

- `findAll()` sem paginação; entidade inteira onde bastava projeção.
- Associação `EAGER` (default do `@ManyToOne`) puxando o grafo inteiro.
- `save` em laço sem batch.
- Cliente HTTP criado a cada chamada (`RestClient.create()` no método).
- Serialização JSON dentro de laço; `String` concatenada com `+` em laço.
- Coluna de filtro frequente sem índice — aponte, mesmo que a correção seja uma migration.

### Duplicação

Critério em "Reuso", `code-style.md`. Sinais: bloco quase idêntico em classes diferentes, Util reimplementado localmente, mapeamento DTO↔entidade à mão em vários pontos (caso de MapStruct), DTO duplicado do que já existe na biblioteca compartilhada.

### Código morto

Classe, método ou parâmetro sem chamador é achado — e costuma ser o mais acionável. Antes de reportar defeito numa classe, **procure quem a usa**.

O que esconde o uso no Spring: bean resolvido por tipo, `@EventListener`, `@Scheduled`, endpoint chamado de fora do repositório (o PDV), bean ativado por `@Profile`/`@ConditionalOnProperty`, e o `Fake` de perfil de teste. Se não der para confirmar, reporte como "sem chamador encontrado neste repositório".

### API que recebe a instrução, não o valor

Método cuja assinatura aceita a **instrução** inteira (`executarScript(String sql)`) só oferece o caminho inseguro — não há o que parametrizar. Trate como achado de desenho: o contrato deveria expor a operação de negócio.

## Formato do achado

```
[BP-3] Título curto do problema
Arquivo: src/main/java/.../MinhaClasse.java:42
Veredito: confirmado | precisa-validar | rejeitado
Severidade: Alta | Média | Baixa        (ausente quando o veredito é precisa-validar)
Problema: o que está errado e o que acontece por causa disso
Sugestão: o código ou a orientação de correção
```

### Os três veredictos

O modelo de veredicto e os princípios de severidade são adaptados da [security-audit-skill da Cloudflare](https://github.com/cloudflare/security-audit-skill).

- **confirmado** — rastro completo no código e resultado delimitado: por onde a entrada chega, o que acontece, qual o efeito. Só aqui entra severidade.
- **precisa-validar** — existe um **fato exato não resolvido** ("não localizei chamador neste repositório", "não sei se a coluna aceita nulo em produção"), e por isso sem severidade.
- **rejeitado** — candidato investigado e derrubado, com a razão (divergência já catalogada na skill, escolha que o contexto justifica).

Registrar o rejeitado evita as duas saídas ruins: reportar o duvidoso e virar ruído, ou calar e deixar o leitor sem saber se o ponto foi visto.

### Severidade

**Probabilidade × impacto, nunca desvio de checklist.** Violar uma regra desta skill não é, por si, severidade Alta.

- **Alta** — já causa dano, ou basta chamar o código existente: perda ou corrupção de dado, vazamento entre tenants, exposição de dado pessoal ou credencial, resultado errado em silêncio.
- **Média** — precisa de uma condição a mais: entrada incomum, concorrência, volume, ou um chamador que ainda não existe. Também o que fere a arquitetura sem quebrar nada hoje.
- **Baixa** — não muda comportamento: estilo, nome, divergência de convenção, teste ausente.

Dois discriminadores:

- **Defesa em profundidade ausente é nota de robustez**, não vulnerabilidade — a menos que a camada primária falhe.
- **Capacidade insegura sem exploração é Média, não Alta.** Diga as duas coisas: o que o contrato permite e que não há uso hoje. Sem confirmar a ausência de uso, o veredito é **precisa-validar**.

### Evidência e validação adversarial

Achado precisa de **arquivo e linha**. Suspeita sem localização é comentário.

**Quem valida não deveria ser quem achou.** Encontrar procura motivo para o problema existir; confirmar procura motivo para ele não existir. Trabalhando sozinho — ou com IA —, faça a passada de refutação explicitamente: para cada achado, tente derrubá-lo antes de reportar.

## Auditoria é diferente de revisão

Revisar um diff responde "o que entrou está certo?". Auditar responde "onde este sistema pode ser atacado?" — e não se responde arquivo por arquivo. Comece mapeando:

1. **Fronteiras de confiança** — endpoint, WebSocket, consumidor de fila, leitura de arquivo, resposta de integração, callback de parceiro.
2. **Superfícies de entrada** — parâmetro, corpo, header, nome de arquivo, mensagem, configuração, certificado de cliente.
3. **O que é sensível** — dado pessoal, credencial, valor financeiro, e o que separa um tenant do outro.

Registre a **cobertura**: o que foi examinado e o que ficou de fora. Sem isso, o silêncio do relatório não distingue "não há problema" de "não olhei".
