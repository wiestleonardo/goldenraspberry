# Feature Specification: Intervalo de prêmios dos produtores (Golden Raspberry Awards)

**Feature Branch**: `001-intervalo-premios-produtores`

**Created**: 2026-10-09

**Status**: Draft

**Input**: User description: "API RESTful de leitura da lista de indicados e vencedores da categoria
Pior Filme do Golden Raspberry Awards (desafio Outsera). Na inicialização, a aplicação lê o arquivo
CSV de filmes e insere os dados em um banco embarcado em memória. A API expõe um único recurso que
retorna o produtor com maior intervalo entre dois prêmios consecutivos e o que obteve dois prêmios
mais rápido, no formato JSON exato do enunciado (min/max com producer, interval, previousWin,
followingWin)."

Fonte de autoridade: `challenge/Avaliação - Back-end.pdf` (prevalece sobre esta especificação).

## Clarifications

### Session 2026-10-09

- Q: Quando o mesmo produtor vence com dois filmes diferentes no mesmo ano, isso conta como dois
  prêmios com intervalo 0? → A: Sim. Cada filme vencedor é um prêmio distinto; dois prêmios no
  mesmo ano geram intervalo 0 (`previousWin` = `followingWin`).
- Q: Se o CSV tiver uma linha inválida, a aplicação deve deixar de iniciar? → A: Tratamento
  proporcional ao erro. Linha individualmente inválida (ano não numérico, título ou produtor
  ausente, colunas a mais ou a menos, `winner` diferente de `yes`/vazio) é descartada com log WARN
  informando número da linha e motivo, e a importação continua. Problema estrutural que impede
  interpretar o arquivo com confiança (arquivo inexistente/ilegível, cabeçalho incompatível) falha
  a inicialização. Sem tabela de rejeitados nem reprocessamento. `winner` vazio = não vencedor.
- Q: Nomes que diferem só em maiúsculas/minúsculas são o mesmo produtor? → A: Não. Comparação
  exata, preservando caixa e grafia original; apenas os espaços das extremidades de cada nome são
  removidos. Nenhuma normalização que altere a identidade (sem ignorar caixa). Coberto por teste
  de integração específico.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consultar os intervalos mínimo e máximo entre prêmios (Priority: P1)

O avaliador (consumidor da API) inicia a aplicação com a lista de filmes padrão e consulta o recurso
de intervalos de prêmios. Recebe, em uma única resposta, os produtores com o **menor** intervalo
entre duas vitórias consecutivas (`min`) e os com o **maior** intervalo (`max`), exatamente no
formato definido na página 2 do enunciado.

**Why this priority**: é o único requisito de API do enunciado; sem ele não há entrega.

**Independent Test**: subir a aplicação com o CSV original e consultar o recurso; a resposta deve
ser exatamente a calculada manualmente a partir do arquivo.

**Acceptance Scenarios**:

1. **Given** a aplicação iniciada com o `Movielist.csv` original, **When** o recurso de intervalos
   é consultado, **Then** a resposta tem status de sucesso e o corpo é exatamente
   `{"min":[{"producer":"Joel Silver","interval":1,"previousWin":1990,"followingWin":1991}],
   "max":[{"producer":"Matthew Vaughn","interval":13,"previousWin":2002,"followingWin":2015}]}`.
2. **Given** qualquer dataset, **When** o recurso é consultado, **Then** cada item contém
   exatamente os campos `producer` (texto), `interval`, `previousWin` e `followingWin` (inteiros),
   e o objeto raiz contém exatamente as listas `min` e `max`.
3. **Given** um dataset em que dois ou mais produtores empatam no menor (ou maior) intervalo,
   **When** o recurso é consultado, **Then** todos os empatados aparecem na lista correspondente.

---

### User Story 2 - Resultados corretos para datasets desconhecidos (Priority: P1)

O avaliador substitui o arquivo de filmes por outro conjunto de dados, com cenários diferentes
(vitórias consecutivas, três ou mais vitórias, empates, múltiplos produtores por filme, separadores
mistos, linhas fora de ordem), e a API continua devolvendo resultados corretos.

**Why this priority**: o enunciado avisa que outros conjuntos de dados serão usados na avaliação.

**Independent Test**: iniciar a aplicação apontando para um arquivo alternativo por configuração e
comparar a resposta com o resultado calculado manualmente para aquele arquivo.

**Acceptance Scenarios**:

1. **Given** um produtor com vitórias em 2000, 2001 e 2010, **When** consultado, **Then** são
   considerados apenas os intervalos consecutivos 2000→2001 (1) e 2001→2010 (9) — nunca 2000→2010.
2. **Given** um filme vencedor com produtores `"A, B and C"` ou `"A, B, and C"`, **When**
   importado, **Then** A, B e C são produtores individuais, cada um recebendo a vitória.
3. **Given** linhas fora de ordem cronológica, **When** consultado, **Then** o resultado é idêntico
   ao do mesmo dataset ordenado.
4. **Given** filmes não vencedores, **When** consultado, **Then** eles não influenciam o resultado.
5. **Given** um dataset em que nenhum produtor venceu duas vezes, **When** consultado, **Then** a
   resposta é `{"min":[],"max":[]}` com status de sucesso.
6. **Given** um mesmo produtor com dois intervalos distintos empatados no mínimo (ou máximo),
   **When** consultado, **Then** os dois intervalos aparecem na lista.
7. **Given** um arquivo alternativo configurado, **When** a aplicação inicia, **Then** somente os
   dados desse arquivo são carregados (o arquivo padrão não é lido).

---

### User Story 3 - Importação previsível do arquivo na inicialização (Priority: P2)

O operador inicia a aplicação; o arquivo é lido uma única vez e os filmes ficam disponíveis no banco
em memória. Linhas individualmente inválidas são descartadas com aviso em log (linha e motivo), sem
impedir a importação das demais. Se o arquivo estiver ausente ou com cabeçalho incompatível, a
aplicação não sobe e informa claramente o problema.

**Why this priority**: garante que um dado ruim nunca produza um resultado errado em silêncio.

**Independent Test**: iniciar a aplicação com um arquivo contendo uma linha inválida e verificar que
apenas as linhas válidas entram no cálculo; iniciar com cabeçalho incompatível e verificar a falha.

**Acceptance Scenarios**:

1. **Given** um arquivo válido, **When** a aplicação inicia, **Then** todos os filmes (vencedores e
   não vencedores) são persistidos e a importação não se repete a cada consulta.
2. **Given** um arquivo com uma linha de ano não numérico, número de colunas incorreto, título ou
   produtores vazios, ou indicador de vencedor desconhecido, **When** a aplicação inicia, **Then**
   essa linha é descartada com log WARN (número da linha e motivo), as demais são importadas e o
   resultado considera apenas as linhas válidas.
3. **Given** um caminho de arquivo inexistente, **When** a aplicação inicia, **Then** a
   inicialização falha informando o caminho não encontrado.
4. **Given** um arquivo cujo cabeçalho não é `year;title;studios;producers;winner`, **When** a
   aplicação inicia, **Then** a inicialização falha informando o cabeçalho incompatível.

---

### Edge Cases

- Campo de produtores entre aspas, com espaços excedentes ou com separadores combinados
  (`"A,B , C and  D"`).
- Mesmo produtor citado duas vezes no mesmo filme (conta como uma vitória daquele filme).
- Produtor vencedor uma única vez: não gera intervalo.
- Produtor com três ou mais vitórias: gera um intervalo por par de vitórias consecutivas.
- Mesmo produtor vencendo com filmes diferentes no mesmo ano: dois prêmios, intervalo 0.
- Nomes que diferem apenas em caixa (`Joel Silver` × `joel silver`): produtores distintos.
- Empate entre produtores e empate entre intervalos do mesmo produtor, no mínimo e no máximo.
- Mínimo e máximo iguais (apenas um intervalo em todo o dataset): o mesmo item aparece em `min` e
  em `max`.
- Arquivo apenas com cabeçalho, ou sem vencedores: listas vazias.
- Indicador de vencedor com variação de caixa ou espaços (`"Yes"`, `" yes "`).
- Linhas em branco no final do arquivo.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST ler o arquivo de filmes uma única vez, durante a inicialização, e
  persistir todos os registros no banco de dados embarcado em memória.
- **FR-002**: O arquivo MUST seguir o layout `year;title;studios;producers;winner` com linha de
  cabeçalho, separador ponto e vírgula e codificação UTF-8; campos entre aspas MUST ser aceitos.
- **FR-003**: O caminho do arquivo MUST ser configurável sem alteração de código; o padrão é o
  `Movielist.csv` original, empacotado com a aplicação.
- **FR-004**: Um filme MUST ser considerado vencedor quando o indicador `winner` for `yes`
  (ignorando caixa e espaços) e não vencedor quando vazio; qualquer outro valor é linha inválida.
- **FR-005**: Linha individualmente inválida (ano ausente ou não numérico, título ou produtores
  vazios, número de colunas diferente de 5, indicador de vencedor desconhecido) MUST ser descartada
  com log de aviso contendo o número da linha e o motivo, e a importação MUST continuar; nenhuma
  linha é descartada silenciosamente. Linhas totalmente em branco são ignoradas.
- **FR-005a**: Arquivo inexistente/ilegível ou cabeçalho diferente de
  `year;title;studios;producers;winner` MUST interromper a inicialização com mensagem clara.
- **FR-006**: O sistema MUST separar os produtores de cada filme em nomes individuais usando como
  separadores a vírgula, a palavra `and` isolada por espaços e a combinação `, and`, removendo
  os espaços das extremidades de cada nome. Nomes são comparados exatamente (caixa e grafia
  preservadas); o mesmo nome repetido no mesmo filme conta uma vez.
- **FR-007**: Para cada produtor, o sistema MUST ordenar suas vitórias por ano e calcular o
  intervalo (`followingWin - previousWin`) somente entre vitórias consecutivas. Cada filme vencedor
  é um prêmio; duas vitórias no mesmo ano geram intervalo 0.
- **FR-008**: A lista `min` MUST conter todos os intervalos iguais ao menor intervalo encontrado; a
  lista `max`, todos os iguais ao maior.
- **FR-009**: Sem nenhum intervalo calculável, o sistema MUST responder `{"min":[],"max":[]}` com
  status de sucesso.
- **FR-010**: Os itens de cada lista MUST ser ordenados de forma determinística: por `producer`
  (ordem alfabética), depois `previousWin` e `followingWin` crescentes.
- **FR-011**: A API MUST expor um único recurso de leitura, acessado por `GET`, seguindo o nível 2
  de maturidade de Richardson (recurso identificado por URI, verbo HTTP semântico, códigos de
  status adequados, JSON).
- **FR-012**: O corpo de resposta MUST conter exatamente os campos do enunciado: `min` e `max` na
  raiz; `producer`, `interval`, `previousWin` e `followingWin` em cada item.
- **FR-013**: Erros de requisição (rota inexistente, método não suportado) MUST responder com o
  status HTTP correspondente (404, 405) e corpo de erro padronizado, sem expor detalhes internos.
- **FR-014**: Nenhum resultado ou regra específica do dataset original pode existir no sistema.

### Key Entities

- **Filme**: ano, título, estúdios, texto original dos produtores e indicador de vencedor.
- **Produtor**: nome individual normalizado, extraído do texto de produtores de um filme.
- **Vitória de produtor**: associação produtor × ano de um filme vencedor.
- **Intervalo de prêmios**: produtor, ano da vitória anterior, ano da vitória seguinte e diferença
  em anos entre elas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com o arquivo original, a resposta é idêntica ao resultado calculado manualmente
  (Joel Silver 1990→1991 em `min`; Matthew Vaughn 2002→2015 em `max`).
- **SC-002**: 100% dos datasets alternativos de teste (consecutivas, 3+ vitórias, empates mínimo e
  máximo, múltiplos empates do mesmo produtor, múltiplos produtores, separadores mistos, não
  vencedores, anos repetidos, ausência de intervalos, ordem aleatória, substituição de arquivo)
  produzem exatamente o resultado esperado calculado à mão.
- **SC-003**: A mesma entrada produz sempre a mesma resposta, byte a byte, em execuções repetidas.
- **SC-004**: Em 100% dos casos testados, linha inválida é descartada sem afetar as válidas, e
  arquivo ausente ou com cabeçalho incompatível impede a inicialização com mensagem clara.
- **SC-005**: O projeto é executado e testado sem instalação de banco ou serviço externo, apenas com
  as instruções do README.
- **SC-006**: A consulta responde em menos de 1 segundo com o dataset original em máquina comum.

## Assumptions

- O formato do arquivo alternativo é o mesmo do original (mesmas colunas e separador); colunas
  extras ou ausentes tornam a linha inválida.
- Nomes de produtores são comparados exatamente, removendo apenas espaços das extremidades,
  diferenciando maiúsculas e minúsculas (`Joel Silver` ≠ `joel silver`) — o PDF não define
  equivalência entre grafias.
- O mesmo produtor citado mais de uma vez no mesmo filme recebe uma única vitória por filme.
- Vitórias do mesmo produtor com filmes diferentes no mesmo ano são prêmios distintos (intervalo 0).
- Política de dados inválidos (o PDF não a define): descarte com WARN por linha; falha apenas para
  problema estrutural do arquivo. Não há tabela de rejeitados nem reprocessamento.
- Não há autenticação: o enunciado não a exige e a API só expõe dado público.
- O recurso não é paginado: o resultado contém apenas os empates de mínimo e máximo.
- Apenas o arquivo é fonte de dados; não há operações de escrita pela API.
