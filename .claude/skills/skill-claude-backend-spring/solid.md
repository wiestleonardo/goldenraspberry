# SOLID — obrigatório

Os cinco princípios SOLID são **requisito**, não sugestão, em todo código novo e em toda refatoração. Boa parte da estrutura desta skill (par interface + `Impl`, Service de responsabilidade única, `ServiceTransactional` separado, `@Qualifier` próprio) já é SOLID aplicado — este arquivo torna isso explícito.

Referência de apoio:
- https://www.alura.com.br/artigos/solid
- https://medium.com/@fabio.alvaro/solid-com-java-spring-boot-como-aplicar-os-princ%C3%ADpios-no-desenvolvimento-de-software-a14c1b544e28

Antes dos princípios, o critério que evita o erro mais comum: **SOLID serve para o código absorver mudança sem quebrar o que já funciona.** Se uma aplicação de SOLID não reduz o risco de uma mudança futura — só adiciona interface, classe ou camada —, ela não resolve nada. Abstração sem um segundo caso de uso real (presente ou claramente previsto) é custo.

## S — Responsabilidade única

Uma classe muda por **um** motivo:

| Classe | Muda quando… |
|---|---|
| `XxxController` | o contrato HTTP muda |
| `XxxFacade` | a ordem ou o desvio do fluxo entre domínios muda |
| `XxxService` | a regra de negócio muda |
| `XxxServiceApplication` | o cálculo/derivação muda |
| `XxxServiceTransactional` | o limite da transação muda |
| `XxxRepository` / `XxxRepositoryImpl` | o acesso ao dado muda |
| `XxxIntercommService` | o contrato com o parceiro muda |
| `XxxMapper` | o mapeamento entre dois formatos muda |

Sinal de violação: para mudar a regra de comissão você precisa reler código de transação, de chamada HTTP e de montagem de JSON. Outro sinal, muito visível com injeção por construtor: **um construtor com 10+ dependências**. Ele não é problema de estilo — é a classe dizendo que tem mais de um motivo para mudar.

Cuidado com o contrário: quebrar uma classe em três só porque ficou grande, sem que cada pedaço tenha motivo próprio de mudar, é fragmentação. O teste é a frase "esta classe muda quando ____": se você não completa sem "e", a divisão está certa; se precisa inventar um motivo, não estava.

## O — Aberto/fechado

Comportamento novo entra como **classe nova**, não como `if`/`switch` novo dentro de uma classe existente.

O caso real da base é o meio de pagamento e o PSP do PIX: cada um é uma implementação da mesma interface, escolhida por `@Qualifier` (ponto de injeção fixo) ou por um `Map<XxxEnum, Interface>` montado num `@Bean` (escolha em runtime). Acrescentar um meio ou um PSP novo significa **criar** uma implementação e registrá-la — nenhuma das existentes é tocada.

```java
// FECHADO PARA MODIFICAÇÃO: nada aqui muda quando entra um PSP novo
PixClient pixClient = this.mapaPixClientPorPSP.get(pspEnum);

// VIOLAÇÃO: cada PSP novo obriga a editar (e arriscar) este método
if (PSPEnum.BANCO_A == pspEnum) { ... }
else if (PSPEnum.BANCO_B == pspEnum) { ... }
```

Quando as variantes são poucas, estáveis e fechadas, `switch` sobre `enum` é aceitável e mais legível — o princípio mira o que **varia com frequência**.

## L — Substituição de Liskov

Quem recebe a interface usa **qualquer** implementação sem saber qual é.

Violações típicas:
- Uma implementação lança `UnsupportedOperationException` num método que a interface promete.
- Uma devolve `null` onde as outras devolvem lista vazia ou `Optional.empty()`.
- Uma exige que outro método seja chamado antes.
- O `Fake` de perfil de teste que se comporta diferente da real em algo que o teste não percebe — mais um motivo para o dublê ficar em `src/test`.

Se o chamador precisa saber **qual** implementação recebeu, ou o desenho está errado, ou as classes não deveriam compartilhar interface.

## I — Segregação de interface

Nenhum cliente depende de método que não usa.

- Ao criar uma implementação nova você escreve métodos vazios ou `throw new UnsupportedOperationException()`.
- Um mock precisa stubar 6 métodos para exercitar 1.
- **`CrudRepository` em tabela por empresa** entrega ao Service `findAll()`, `findById()` e `deleteAll()` sem filtro de tenant — métodos que o Service não deveria ter à mão. Estender `Repository<T, ID>` e declarar só o que o negócio usa é segregação de interface que também é segurança.

Não confunda com contagem de métodos: oito métodos coesos, usados juntos pelo mesmo fluxo, estão corretos.

## D — Inversão de dependência

Dependa da abstração, nunca da implementação:

```java
// CERTO — o campo é do tipo da interface
private final IntegracaoCabecalhoService integracaoCabecalhoService;

// ERRADO — acopla na implementação concreta
private final IntegracaoCabecalhoServiceImpl integracaoCabecalhoServiceImpl;
```

É daqui que vem o valor do par interface + `Impl`: o Service depende do **contrato** do Repository, não de ele usar QueryDSL ou `JdbcClient`.

Vale também para o que não é camada: hora atual, geração de identificador e chamada externa entram por abstração injetada — não por `LocalDateTime.now()` fincado no meio da regra, que torna o trecho impossível de testar. Para a hora, o `Clock` é um `@Bean` no fuso de Brasília (`Clock.system(DateUtil.FUSO_BRASILIA)`), recebido pelo construtor e usado como `LocalDateTime.now(this.clock)`; no teste, `Clock.fixed(...)` congela o horário — inclusive o das 23h30, que vira o dia seguinte em UTC.

A injeção por construtor torna a inversão **visível**: a lista de parâmetros é a lista de abstrações das quais a classe depende.

## Como isso entra na revisão

1. **Consigo dizer em uma frase, sem "e", por que esta classe mudaria?** (S)
2. **Para adicionar o próximo caso deste tipo, vou criar algo ou editar algo que já funciona?** (O)
3. **Quem me injeta depende do meu contrato ou de como eu faço?** (D, e por tabela L e I)

Quando a resposta apontar violação, o caminho costuma ser o que a skill já descreve: extrair um Service com responsabilidade própria, mover orquestração para a Facade, criar um qualificador em vez de um `if`, ou separar a interface. Não invente estrutura nova para satisfazer o princípio — as camadas de `layers-and-naming.md` cobrem os casos previstos.
