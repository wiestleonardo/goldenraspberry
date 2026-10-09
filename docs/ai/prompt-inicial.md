# Prompt inicial (texto integral enviado pelo usuário)

# Desenvolvimento do desafio técnico Outsera — Back-end Java

Implemente integralmente o desafio técnico disponível em `D:\outsera`, utilizando Claude Code, GitHub Spec Kit e obrigatoriamente a skill `skill-claude-backend-spring`.

Conduza o desenvolvimento desde a análise dos requisitos até a validação final, seguindo práticas profissionais de engenharia de software, com foco em correção funcional, simplicidade, performance, qualidade e aderência aos padrões definidos no projeto.

Trabalhe de forma autônoma e incremental. Não encerre após criar especificações ou implementar o primeiro endpoint. Continue até concluir todas as etapas viáveis e produzir uma entrega pronta para revisão.

Toda a comunicação, documentação e registros técnicos devem estar em português brasileiro, respeitando as convenções de nomenclatura estabelecidas na skill.

## 1. Fontes obrigatórias

Antes de qualquer implementação, leia integralmente:

- `challenge/Avaliação - Back-end.pdf`
- `challenge/Movielist.csv`
- `.claude/skills/skill-claude-backend-spring/SKILL.md`

Consulte os arquivos complementares da skill conforme o contexto:

- `layers-and-naming.md`
- `code-style.md`
- `database.md`
- `testing.md`
- `solid.md`
- `stack-and-build.md`
- `security-and-review.md`
- `cloud-native.md`
- `threads-and-virtual-threads.md`

A skill define os padrões obrigatórios para todo código Java e Spring Boot escrito ou revisado neste projeto.

Não utilize padrões genéricos quando houver uma convenção correspondente na skill.

Não suponha a existência de bibliotecas internas, utilitários proprietários ou classes compartilhadas que não estejam disponíveis no projeto.

## 2. Hierarquia de autoridade

Respeite rigorosamente:

1. Requisitos expressos no PDF da Outsera.
2. Padrões obrigatórios da skill `skill-claude-backend-spring`.
3. Constitution do GitHub Spec Kit.
4. Especificação e planejamento técnico produzidos para este projeto.

Em caso de conflito, o requisito explícito do desafio prevalece.

As exceções necessárias aos padrões da skill devem ser documentadas e justificadas.

Não altere a skill para adaptá-la artificialmente ao desafio.

## 3. Processo de desenvolvimento — Spec Kit

Utilize os recursos oficiais do GitHub Spec Kit disponíveis no projeto.

Conduza as seguintes fases:

1. Constitution.
2. Specify.
3. Clarify.
4. Plan.
5. Tasks.
6. Analyze.
7. Implement.
8. Validação e revisão final.

Utilize os mecanismos efetivamente disponíveis na instalação. Não simule comandos ou execução de skills.

Gere e atualize automaticamente os artefatos do Spec Kit.

Não solicite criação manual de documentos que possam ser produzidos pela ferramenta.

A Constitution deve estabelecer os princípios de engenharia e referenciar a skill como fonte obrigatória dos padrões de implementação.

A especificação deve descrever requisitos funcionais, regras de negócio, contrato da API, critérios de aceitação e casos extremos.

O planejamento deve apresentar decisões técnicas fundamentadas, alternativas consideradas e trade-offs.

As tarefas devem ser incrementais, verificáveis e relacionadas aos requisitos.

Antes de implementar, verifique a consistência entre Constitution, especificação, planejamento e tarefas.

Solicite esclarecimento humano somente quando uma ambiguidade puder alterar o comportamento exigido ou houver uma decisão relevante que não possa ser inferida das fontes disponíveis.

## 4. Princípios de engenharia

Aplique rigorosamente:

- SOLID, conforme a skill.
- KISS.
- YAGNI.
- DRY.
- Alta coesão.
- Baixo acoplamento.
- Responsabilidade única.
- Código Java idiomático.
- Separação objetiva de responsabilidades.
- Determinismo.
- Performance proporcional ao problema.
- Tratamento previsível de erros.
- Reprodutibilidade.
- Dependências mínimas.
- Manutenibilidade.
- Segurança.

Evite overengineering.

Não crie microsserviços, arquitetura hexagonal completa, CQRS, Event Sourcing, mensageria, cache distribuído, estratégias, factories, interfaces ou camadas adicionais sem necessidade concreta.

Aplique as interfaces, implementações e camadas exigidas pela skill quando forem pertinentes à responsabilidade implementada.

Não transforme o projeto em uma arquitetura corporativa artificial.

## 5. Stack tecnológica

Utilize:

- Java 21, disponível no ambiente.
- Spring Boot 4.x estável e compatível.
- Spring MVC.
- Maven com Maven Wrapper.
- H2 embarcado em memória.
- JUnit 5.
- Infraestrutura de testes de integração compatível com Spring Boot 4.
- Docker.
- GitHub Actions.

Defina a estratégia de persistência conforme `database.md`, considerando simplicidade, legibilidade e necessidade real.

Avalie Spring Data JPA e `JdbcClient` de acordo com os critérios da skill.

Utilize bibliotecas consolidadas para parsing de CSV, quando necessário.

Não introduza dependências externas de infraestrutura.

Não utilize Java 25 sem verificar sua disponibilidade e obter autorização para modificar a versão Java do projeto.

Verifique a compatibilidade das dependências e propriedades com a versão efetiva do Spring Boot.

## 6. Requisitos funcionais

Implemente a API RESTful definida no PDF.

A aplicação deve:

1. Ler o CSV durante a inicialização.
2. Persistir os filmes em H2 em memória.
3. Identificar os filmes vencedores.
4. Identificar corretamente os produtores individuais.
5. Calcular intervalos entre premiações consecutivas do mesmo produtor.
6. Identificar os menores intervalos.
7. Identificar os maiores intervalos.
8. Preservar todos os resultados empatados.
9. Retornar exatamente o contrato JSON especificado.
10. Funcionar com datasets diferentes do original.

Siga o nível 2 do modelo de maturidade de Richardson.

Não adicione endpoints sem necessidade.

A API deve retornar os campos:

- `min`
- `max`
- `producer`
- `interval`
- `previousWin`
- `followingWin`

Preserve exatamente a estrutura exigida pelo PDF.

Não utilize resultados fixos ou regras especiais para os produtores presentes no dataset original.

Permita substituir o arquivo CSV por configuração sem alterar o código-fonte.

Mantenha o CSV original como dataset padrão.

## 7. Regras críticas do algoritmo

Analise e valide especialmente:

- Produtores separados por vírgulas.
- Produtores separados por `and`.
- Combinações de separadores.
- Campos CSV com aspas.
- Espaços excedentes.
- Filmes com múltiplos produtores.
- Produtores vencedores uma única vez.
- Produtores com três ou mais vitórias.
- Intervalos de vitórias consecutivas.
- Intervalos mínimos empatados.
- Intervalos máximos empatados.
- Múltiplos intervalos empatados do mesmo produtor.
- Vitórias em anos repetidos.
- Registros fora de ordem.
- Filmes não vencedores.
- Ausência de intervalos válidos.
- Datasets não conhecidos previamente.

Determine explicitamente o comportamento das ambiguidades do enunciado.

Não calcule intervalos arbitrários entre qualquer combinação de anos.

Ordene os resultados de maneira determinística.

Não introduza processamento paralelo ou cache sem evidência real de necessidade.

## 8. Persistência e importação

A persistência deve funcionar integralmente em memória, sem instalação externa.

A importação deve ocorrer no startup e não a cada requisição.

Implemente:

- Leitura correta do CSV.
- Validação dos dados necessários.
- Identificação dos vencedores.
- Persistência consistente.
- Tratamento previsível de erros de importação.
- Substituição do CSV por configuração.
- Consultas eficientes para a regra de negócio.

Evite consultas N+1.

Evite acesso repetitivo ao banco dentro de loops quando uma consulta puder fornecer os dados necessários.

Não descarte silenciosamente linhas inválidas.

Evite modelagem e índices desnecessários para o problema.

## 9. Performance

Priorize correção e eficiência.

Considere:

- Complexidade algorítmica.
- Utilização adequada de coleções.
- Consultas SQL eficientes.
- Ordenação de premiações.
- Percurso linear dos intervalos consecutivos.
- Uso proporcional de memória.
- Ausência de processamento redundante.

Procure manter uma complexidade compatível com agrupamento e ordenação, aproximadamente O(N + W log W), quando aplicável à modelagem escolhida.

Não otimize prematuramente.

Documente resumidamente a complexidade efetiva do algoritmo e os trade-offs relevantes.

## 10. Testes exclusivamente de integração

O PDF determina a implementação somente de testes de integração.

Não crie testes unitários.

Os testes devem exercitar efetivamente:

- Inicialização Spring.
- Importação do CSV.
- Persistência H2.
- Regras de negócio.
- Endpoint HTTP real.
- Contrato JSON.

Crie datasets alternativos independentes que cubram:

1. Resultado do CSV original.
2. Vitórias consecutivas.
3. Três ou mais vitórias do mesmo produtor.
4. Empates mínimos.
5. Empates máximos.
6. Múltiplos empates do mesmo produtor.
7. Múltiplos produtores por filme.
8. Separadores mistos de nomes.
9. Filmes não vencedores.
10. Anos repetidos.
11. Ausência de intervalos.
12. Dados em ordem aleatória.
13. Substituição do dataset.
14. Contrato HTTP e JSON.

Os resultados esperados dos testes devem ser definidos independentemente do algoritmo da aplicação.

Os testes devem ser isolados e reproduzíveis.

Respeite a nomenclatura e o estilo de testes da skill.

Execute o build completo e corrija falhas antes de considerar a implementação concluída.

## 11. Docker

Implemente:

- Dockerfile multi-stage.
- `.dockerignore`.
- Build reproduzível.
- Runtime Java 21.
- Execução sem banco externo.
- Configuração apropriada de porta.
- Usuário não root, quando viável.

Valide efetivamente:

1. Construção da imagem.
2. Inicialização do container.
3. Disponibilidade da API.
4. Resposta JSON.
5. Encerramento do container.

Não declare que o Docker funciona sem executar as verificações.

## 12. GitHub Actions

Configure uma pipeline simples que:

- Utilize Java 21.
- Execute Maven Wrapper.
- Compile a aplicação.
- Execute os testes de integração.
- Falhe quando houver erros.

Não implemente deploy.

Não adicione ferramentas desnecessárias.

## 13. Qualidade e conformidade com a skill

A cada incremento relevante, revise o código contra a skill.

Valide:

- Organização dos packages.
- Responsabilidades das camadas.
- Nomenclatura.
- Interfaces e implementações.
- Injeção por construtor.
- Campos `final`.
- Convenção obrigatória de `this.`.
- Organização de métodos e atributos.
- Linhas em branco.
- SQL e persistência.
- Transações.
- DTOs.
- Tratamento de erros.
- Logs.
- Testes.
- SOLID.
- Segurança.

Não introduza dependências, abstrações ou padrões desnecessários.

Na revisão final, produza uma verificação explícita de conformidade com a skill.

Documente apenas desvios reais e justificados.

## 14. Registro das interações com IA

O desafio exige o registro de interações com o agente.

Crie a organização:

`docs/ai/`

Registre objetivamente:

- Ferramentas utilizadas.
- Skills utilizadas.
- Principais prompts reais.
- Histórico de sessões, quando disponível.
- Decisões técnicas.
- Alternativas analisadas.
- Revisões relevantes.
- Problemas encontrados.
- Correções realizadas.
- Resultados das validações.

Não invente conversas, prompts ou resultados.

Não apresente resumos como transcrições originais.

Utilize exportações autênticas das sessões quando possível.

Não inclua credenciais ou dados sensíveis.

O README deve conter um link para essas evidências.

## 15. Disponibilização da skill

A skill está localizada em:

`.claude/skills/skill-claude-backend-spring/`

Ela é parte obrigatória da entrega.

Mantenha todos os documentos relevantes versionados no repositório.

Não inclua o `.git` do repositório original da skill.

Não altere o conteúdo original da skill.

Inclua no README uma seção sobre os padrões de engenharia adotados, com link para o arquivo `SKILL.md`.

A Constitution também deve referenciar essa skill.

## 16. Documentação

Crie README em português brasileiro, contendo:

- Objetivo do projeto.
- Stack tecnológica.
- Pré-requisitos.
- Como executar localmente.
- Como executar os testes.
- Como executar com Docker.
- Endpoint disponível.
- Exemplo de JSON.
- Como substituir o CSV.
- Arquitetura resumida.
- Decisões técnicas relevantes.
- Padrões aplicados.
- Link para a skill.
- Evidências de utilização da IA.

As instruções devem corresponder ao funcionamento real da aplicação.

Não faça afirmações de sucesso sem validação.

## 17. Git e organização

Configure `.gitignore` para Java, Maven, IDEs e arquivos temporários.

Preserve:

- Código-fonte.
- Testes de integração.
- CSV padrão.
- Documentação.
- Skill.
- Artefatos relevantes do Spec Kit.
- Evidências de utilização da IA.
- Pipeline GitHub Actions.
- Maven Wrapper.
- Dockerfile.

Não inclua arquivos temporários, artefatos de build ou credenciais.

Organize commits de maneira incremental e coerente.

Não publique automaticamente no GitHub sem autorização explícita.

## 18. Revisão técnica final

Ao concluir a implementação, realize uma revisão de código rigorosa.

Verifique:

- Aderência integral ao PDF.
- Precisão dos cálculos.
- Compatibilidade com datasets desconhecidos.
- Separação correta dos produtores.
- Tratamento de empates.
- Contrato JSON.
- Nível REST exigido.
- Persistência em memória.
- Performance.
- SQL.
- Transações.
- SOLID.
- Simplicidade.
- Conformidade com a skill.
- Qualidade dos testes.
- Docker.
- CI.
- README.
- Evidências de IA.

Classifique problemas por severidade.

Corrija defeitos reais e execute novamente as validações afetadas.

Não reescreva código funcional apenas por preferência estética.

## 19. Critérios de conclusão

Considere a tarefa concluída somente quando:

- Os requisitos do PDF estiverem atendidos.
- A aplicação compilar.
- Todos os testes de integração passarem.
- A API retornar o JSON esperado.
- Datasets alternativos estiverem validados.
- O banco H2 funcionar em memória.
- A aplicação iniciar localmente.
- O Docker construir e executar.
- O endpoint funcionar dentro do container.
- A pipeline de CI estiver configurada.
- O README estiver completo.
- A skill estiver incluída.
- As interações reais com IA estiverem documentadas.
- A revisão técnica final estiver concluída.

Caso alguma validação não possa ser executada, sinalize claramente a pendência.

## 20. Execução

Trabalhe autonomamente em etapas incrementais.

Não pare após o planejamento.

Não pare após gerar o código inicial.

Não pare após os primeiros testes passarem.

Continue até concluir todas as etapas viáveis.

Evite pedir confirmação para decisões técnicas rotineiras, mas solicite esclarecimentos para ambiguidades de negócio relevantes ou ações externas e destrutivas.

Não simule comandos do Spec Kit.

Não declare testes executados sem evidência.

Não altere requisitos para facilitar a implementação.

Ao final, apresente um relatório resumido com:

1. Funcionalidades implementadas.
2. Arquitetura utilizada.
3. Conformidade com a skill.
4. Testes executados e resultados.
5. Resultado da validação Docker.
6. Estado da CI.
7. Localização das evidências de IA.
8. Pendências e riscos.
9. Situação de preparação para entrega.

**Inicie agora lendo os arquivos originais, verificando o ambiente e a skill, estabelecendo a Constitution e conduzindo o fluxo oficial do Spec Kit até a validação final.**
