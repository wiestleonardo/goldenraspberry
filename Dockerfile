# Etapa de build: compila com o Maven Wrapper do projeto (mesma versão de Maven do desenvolvimento).
# Os testes de integração rodam no build local e na CI (./mvnw verify); aqui só se empacota.
FROM eclipse-temurin:21-jdk-noble AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Etapa de execução: só o JRE 21 e o jar, com usuário sem privilégio.
FROM eclipse-temurin:21-jre-noble

RUN groupadd --system aplicacao && useradd --system --gid aplicacao --no-create-home aplicacao

WORKDIR /app

COPY --from=build /workspace/target/golden-raspberry-api-*.jar app.jar

USER aplicacao

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
