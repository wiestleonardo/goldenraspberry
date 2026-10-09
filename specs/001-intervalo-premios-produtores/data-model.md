# Data Model: Intervalo de prêmios dos produtores

## Tabelas (H2 em memória, `schema.sql`)

### `tb_filme` — entidade `Filme`

| Coluna | Tipo | Nulo | Campo Java | Origem CSV / regra |
|---|---|---|---|---|
| `id_filme` | `BIGINT` (PK `filme_pk`, sequence `gen_filme`) | não | `idFilme` | gerado |
| `nr_ano` | `BIGINT` | não | `ano` | `year` — inteiro > 0 |
| `tx_titulo` | `VARCHAR` | não | `titulo` | `title` — não vazio |
| `tx_estudios` | `VARCHAR` | sim | `estudios` | `studios` — livre |
| `tx_produtores` | `VARCHAR` | não | `produtores` | `producers` — texto original |
| `fl_vencedor` | `BOOLEAN` | não | `vencedor` | `winner` — `yes` (sem caixa) = `TRUE`; vazio = `FALSE` |

Relacionamento: `Filme 1 — N ProdutorFilme` (`@OneToMany(mappedBy = "filme", cascade = PERSIST)`).

### `tb_produtorfilme` — entidade `ProdutorFilme`

| Coluna | Tipo | Nulo | Campo Java | Regra |
|---|---|---|---|---|
| `id_produtorfilme` | `BIGINT` (PK `produtorfilme_pk`, sequence `gen_produtorfilme`) | não | `idProdutorFilme` | gerado |
| `cd_filme` | `BIGINT` (FK `produtorfilme_filme_fk` → `tb_filme`) | não | `filme` (`@ManyToOne(fetch = LAZY)`) | filme de origem |
| `tx_produtor` | `VARCHAR` | não | `produtor` | nome individual, `strip()`, caixa preservada, único por filme |

## Objetos internos

- **`FilmeVO`** (`vo`, `record`): `ano`, `titulo`, `estudios`, `produtores`, `vencedor`,
  `listaProdutor` — linha válida do CSV já separada; transporte da leitura para a escrita.
- **`VitoriaProdutorProjection`** (`repository.projection`, `record`): `produtor` (`String`),
  `ano` (`Long`) — uma linha por produtor × filme vencedor, ordenada por produtor e ano.

## Contrato de saída

- **`RetornoIntervaloPremioDTO`** (`record`): `listaIntervaloMinimo` → JSON `min`;
  `listaIntervaloMaximo` → JSON `max`.
- **`IntervaloPremioDTO`** (`record`): `produtor` → `producer`; `intervalo` → `interval`;
  `anoPremioAnterior` → `previousWin`; `anoPremioSeguinte` → `followingWin`.
- **`ErroDTO`** (`record`): `codigo`, `mensagem` — corpo único de erro.

Nomes Java em português (skill) mapeados por `@JsonProperty` para os nomes exigidos pelo PDF.

## Regras de validação de linha (importação)

| Condição | Ação |
|---|---|
| Arquivo inexistente | Falha de inicialização (`ARQUIVO_FILME_NAO_ENCONTRADO`) |
| Erro de leitura | Falha de inicialização (`ARQUIVO_FILME_ILEGIVEL`) |
| Cabeçalho ≠ `year;title;studios;producers;winner` | Falha de inicialização (`CABECALHO_ARQUIVO_FILME_INVALIDO`) |
| Linha totalmente em branco | Ignorada |
| Colunas ≠ 5 | WARN + descarte |
| `year` ausente, não numérico ou ≤ 0 | WARN + descarte |
| `title` vazio | WARN + descarte |
| `producers` sem nenhum nome após separação | WARN + descarte |
| `winner` diferente de `yes`/vazio | WARN + descarte |

## Ciclo de vida

Os dados são gravados uma vez na inicialização e somente lidos depois; não há atualização nem
exclusão. Cada processo (e cada contexto de teste) tem seu próprio banco em memória.
