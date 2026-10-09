# Quickstart — validação ponta a ponta

## Pré-requisitos

- JDK 21 (`JAVA_HOME` apontando para ele). Maven não precisa estar instalado (Maven Wrapper).
- Docker (opcional, para a validação em container).

## 1. Build e testes de integração

```bash
./mvnw verify            # Windows: mvnw.cmd verify
```

Esperado: `BUILD SUCCESS`, todos os testes de `ProdutorControllerTest` e `ImportacaoFilmeJobTest`
aprovados.

## 2. Execução local com o CSV padrão

```bash
./mvnw spring-boot:run
curl http://localhost:8080/api/produtores/intervalosPremios
```

Esperado: o JSON de [contracts/intervalos-premios.md](contracts/intervalos-premios.md)
(Joel Silver 1990→1991 em `min`; Matthew Vaughn 2002→2015 em `max`).

## 3. Substituição do CSV

```bash
java -jar target/golden-raspberry-api-*.jar --filme.arquivo-csv=file:/caminho/outro.csv
# ou FILME_ARQUIVO_CSV=file:/caminho/outro.csv
```

Esperado: o resultado reflete somente o arquivo informado; linhas inválidas aparecem como WARN no
log; arquivo inexistente ou cabeçalho incompatível impedem a subida.

## 4. Docker

```bash
docker build -t golden-raspberry-api .
docker run --rm -d -p 8080:8080 --name golden-raspberry-api golden-raspberry-api
curl http://localhost:8080/api/produtores/intervalosPremios
docker stop golden-raspberry-api
```

Com outro CSV: montar o arquivo e definir `FILME_ARQUIVO_CSV=file:/dados/outro.csv`
(`-v /caminho:/dados:ro`).
