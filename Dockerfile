# Etapa de build: compila com o Maven Wrapper do projeto (mesma versão de Maven do desenvolvimento).
# Os testes de integração rodam no build local e na CI (./mvnw verify); aqui só se empacota.
# Imagens fixadas por versão e digest: o mesmo build gera a mesma base (atualizar ambos ao subir de versão)
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87 AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Etapa de execução: só o JRE 21 e o jar, com usuário sem privilégio.
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:000fd431958bc81a24abe1e8e5f0f0fd3ae365a594bd50aadb20696805f9408c

RUN groupadd --system aplicacao && useradd --system --gid aplicacao --no-create-home aplicacao

WORKDIR /app

COPY --from=build /workspace/target/golden-raspberry-api-*.jar app.jar

USER aplicacao

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
