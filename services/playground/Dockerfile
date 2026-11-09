# Construction (depuis la racine du dépôt) :  docker build -f services/playground/Dockerfile -t dystos-playground .
# NON TESTÉ dans l'environnement de développement (Docker n'y était pas disponible) : à valider chez vous.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN scripts/build.sh

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home --uid 10001 dystos
WORKDIR /app
COPY --from=build /src/build/dystos-playground.jar /app/dystos-playground.jar
USER 10001
EXPOSE 8080
# Mode « process » : une JVM fille jetable par exécution (mémoire plafonnée, arrêt forcé).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-Dfile.encoding=UTF-8", "-jar", "/app/dystos-playground.jar", \
            "--bind", "0.0.0.0", "--port", "8080", "--mode", "process", "--trust-proxy"]
