# Contrato HTTP — Intervalos de prêmios dos produtores

## `GET /api/produtores/intervalosPremios`

Recurso somente leitura (Richardson nível 2: recurso por URI, verbo `GET` sem efeito colateral,
status HTTP semântico, representação JSON). Sem parâmetros, sem corpo de entrada, sem autenticação.

### 200 OK — `Content-Type: application/json`

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

(valores do `Movielist.csv` original)

Regras:

- Raiz com exatamente `min` e `max`; itens com exatamente `producer` (string), `interval`,
  `previousWin`, `followingWin` (inteiros).
- `min`/`max` contêm **todos** os intervalos iguais ao menor/maior valor; ordenados por
  `producer`, `previousWin`, `followingWin`.
- Sem intervalos: `{"min":[],"max":[]}` (200).
- Com um único intervalo no dataset, ele aparece em `min` e em `max`.

### Erros — corpo padronizado

```json
{ "codigo": "RECURSO_NAO_ENCONTRADO", "mensagem": "Recurso não encontrado." }
```

| Situação | Status | `codigo` |
|---|---|---|
| Rota inexistente | 404 | `RECURSO_NAO_ENCONTRADO` |
| Método diferente de `GET` | 405 | `METODO_NAO_SUPORTADO` |
| Demais erros tratados pelo framework | status do framework | `REQUISICAO_INVALIDA` |
| Falha não prevista | 500 | `ERRO_NAO_TRATADO` |

Stack trace nunca vai no corpo.

