# Skill Claude — Backend Spring

Skill do Claude Code que faz a IA escrever e revisar código Java/Spring Boot no padrão da equipe: estrutura de pacotes e camadas, nomenclatura, tipos de dados, estilo de código, Spring Data/QueryDSL/SQL nativo e Liquibase, testes, segurança, virtual threads e boas práticas cloud native — tudo na stack atual (**Spring Boot 4.x, Java 21+, Java 25 recomendado**).

Sem ela, a IA escreve no padrão genérico da internet — ou, pior, copia o que já existe nos projetos: `@Autowired` em campo, JUnit 4, Swagger 2, ModelMapper, `java.util.Date`, `@Transactional` com chamada HTTP dentro. Com ela, segue o que o time combinou, na versão atual do Spring.

Este README é para pessoas. O que a IA lê é o `SKILL.md` e, sob demanda, os arquivos de referência.

## Instalação

A forma recomendada é **clonar o repositório e apontar o diretório de skills do Claude Code para o clone** com um link — sem copiar arquivos. Assim um `git pull` já atualiza a skill em toda sessão.

### 1. Clonar

```bash
git clone <url-do-repositorio>/skill-claude-backend-spring.git
```

### 2. Criar o link

O nome do link **precisa** ser `skill-claude-backend-spring` (é o `name` declarado no `SKILL.md`). Troque `<pasta-do-clone>` pelo caminho completo do clone.

**PowerShell:**

```powershell
New-Item -ItemType Junction -Path "$env:USERPROFILE\.claude\skills\skill-claude-backend-spring" -Target "<pasta-do-clone>"
```

**Prompt de comando (cmd.exe):**

```
mklink /J "%USERPROFILE%\.claude\skills\skill-claude-backend-spring" "<pasta-do-clone>"
```

**Linux/macOS:**

```bash
ln -s <pasta-do-clone> ~/.claude/skills/skill-claude-backend-spring
```

Se a pasta `~/.claude/skills` ainda não existir, crie-a antes. Junction de diretório não exige permissão de administrador.

### 3. Conferir

```powershell
Get-Item "$env:USERPROFILE\.claude\skills\skill-claude-backend-spring" -Force | Select-Object LinkType, Target
```

Se responder `Junction` com o alvo na pasta do clone, está pronto. Numa sessão nova do Claude Code, pergunte `quais skills estão disponíveis?`.

### Alternativa: dentro de um projeto

Para a skill acompanhar um repositório específico, copie os arquivos (sem `.git`) para `.claude/skills/skill-claude-backend-spring/` do projeto e commite. A cópia não acompanha as atualizações deste repositório.

## Convivência com a skill Quarkus

As duas skills compartilham as regras que são da equipe e não do framework — idioma, tipos de dados, Util no lugar de operador, SQL em caixa alta, Liquibase, segurança, escala. O que difere, de propósito, porque reflete o que cada base sempre fez ou o que cada framework recomenda:

| Assunto | Quarkus | Spring |
|---|---|---|
| Injeção | `@Inject` no campo, visibilidade de pacote | construtor com campo `private final` (recomendação do Spring) |
| `this.` | só em chamada a método privado | em **todo** acesso a campo e método da própria classe (padrão da base Spring) |
| Facade | a chamada externa síncrona | o **orquestrador** entre Controller e Services; a chamada externa é o `IntercommService` + `Client` |
| Exceção | `CloudException`/`CloudMessage`/`MessageBuilder` | `BusinessServiceException` pela fábrica da biblioteca compartilhada |
| Log | `io.quarkus.logging.Log` | Lombok `@Slf4j` |
| Enum | `value` + `fromValue` | `codigo` + `getByCodigo` |
| Sequence | `seq_<tabela>` | `gen_<tabela>` (padrão dos schemas Spring) |
| Teste | `metodoSucesso`/`metodoFalha` | `deve<Resultado>Quando<Condicao>` |

As duas skills disparam por contexto: a IA usa a Spring num projeto com `spring-boot` no `pom.xml` e a Quarkus num com `quarkus`.

## Como ela dispara

Sozinha, quando a conversa envolve código Java/Spring Boot — criar classe, decidir camada, revisar MR, migrar versão, dúvida de nomenclatura, concorrência, segurança ou escala. Para forçar, cite pelo nome: `usando a skill de convenções Spring, crie o Service de X`.

## Mapa dos arquivos

| Arquivo | Conteúdo | Quando a IA lê |
|---|---|---|
| `SKILL.md` | Índice: stack, estrutura de pacotes, nomenclatura por camada, tipos de dados, idioma, divergências | Sempre |
| `layers-and-naming.md` | Cada camada com exemplo completo, injeção por construtor, exceções, Client HTTP | Ao criar ou revisar uma classe |
| `code-style.md` | Formatação, `this.`, logs, datas e fuso, Util, transações, configuração | Ao escrever código |
| `database.md` | Spring Data × QueryDSL × `JdbcClient`, SQL, nomenclatura de banco, Liquibase | Ao mexer em query ou schema |
| `testing.md` | JUnit 5, Mockito, `@WebMvcTest`, `@DataJpaTest` + Testcontainers, migração do JUnit 4 | Ao escrever ou cobrar teste |
| `stack-and-build.md` | Versões, starters do Boot 4, `pom.xml`, Jackson 3, observabilidade, nativo, roteiro de migração | Ao criar projeto, mexer no build ou migrar |
| `solid.md` | Os cinco princípios ancorados nos padrões daqui | Em revisão de desenho |
| `security-and-review.md` | Regras de segurança, Spring Security, checklist de revisão Java | Em revisão e em código exposto |
| `threads-and-virtual-threads.md` | Virtual threads no Spring, `@Async`, `@ConcurrencyLimit`, pinning | Em código concorrente |
| `cloud-native.md` | Escala, ShedLock, Actuator, alternativas GCP/AWS | Ao avaliar se aguenta produção |

## De onde vieram as regras

Análise do código dos serviços Spring Boot da Equipe Veraz (venda, documento eletrônico, periféricos, agente, repositório compartilhado do PDV e PIX), dos `CLAUDE.md` desses projetos, e das decisões de padrão da equipe — atualizadas para o Spring Boot 4 e o Java 21+. Onde a prática atual e a convenção divergem, a skill documenta as duas coisas: a regra a seguir, e a seção **"Divergências conhecidas — não copiar"** no `SKILL.md`.

Isso é proposital. Sem essa seção, a IA lê o código existente e conclui que aquilo é o padrão — e a base tem mais de 6 mil `@Autowired` em campo para convencê-la.

## Contribuir

As convenções são da equipe, não do arquivo — se um padrão mudar, o arquivo muda junto. Mudança de regra entra por merge request.

- **Uma regra tem um dono.** Se o assunto já está em outro arquivo, aponte para lá em vez de repetir.
- **Explique o porquê, não só o quê.**
- **Exemplo de código precisa seguir as próprias convenções da skill** — injeção por construtor, `this.` em todo membro, linha em branco no início do bloco, `public abstract` em interface, nome de variável igual ao tipo, Util no lugar de operador. Exemplo fora do padrão é o que a IA vai copiar.
- **Nada de nome de cliente ou dado real nos exemplos** — use domínio genérico (`Pedido`, `Cliente`, `ComandaEletronica`).
- **Afirmação sobre o Spring tem fonte.** Nome de propriedade, starter e anotação mudam entre versões; confira na documentação da versão antes de escrever.
- **Mudou de ideia sobre uma regra?** Atualize a regra e reveja a lista de divergências.
- **Arquivos em UTF-8 sem BOM.**

Depois de editar, peça à IA para criar uma classe que exercite o que mudou e veja se o resultado bate.

## Pontos de atenção

- **A skill pede mais do que a base faz hoje** — Bean Validation, `@ConfigurationProperties`, Actuator, virtual threads, teste nos projetos novos. É intencional; espere a IA sugerir isso em código novo.
- **Projetos em Boot 2.7/3.x**: a IA escreve no padrão da skill **dentro do que a versão do projeto permite** (ex.: sem `@ImportHttpServices` num Boot 3.4) e aponta o que depende da migração — que é tarefa própria, não de carona.
- **Segurança**: a análise encontrou segredo em código e em arquivo versionado em mais de um projeto. A skill manda corrigir ao tocar e avisar o time, porque segredo commitado precisa de rotação, não só de remoção.
