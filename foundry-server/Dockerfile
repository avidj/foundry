# ---- build stage ----
# build with 'docker build -f foundry-server/Dockerfile -t foundry-server .' or docker compose
# run with 'docker run -p 7070:7070 -e DATA_DIR=/data foundry-server' or docker compose

# base image with maven:3.0 and tem:21 preinstalled, build is just an alias for later reference
FROM maven:3.9-eclipse-temurin-21 AS build
# working directory insid ethe container, subsequent ops are relative to /app, create if not exists
WORKDIR /app

# Copy pom files first — lets Docker cache the dependency layer separately from source
# copy pom files into folder structure, create if not exists
COPY pom.xml .
COPY foundry-storage/pom.xml foundry-storage/
COPY foundry-kvstore/pom.xml foundry-kvstore/
COPY foundry-bitcask/pom.xml foundry-bitcask/
COPY foundry-lsm/pom.xml foundry-lsm/
COPY foundry-streaming/pom.xml foundry-streaming/
COPY foundry-server/pom.xml foundry-server/
# run maven inside the container to download all deps into local .m2, '-pl foundry-server -am' -> only server and upstream, '-q' suppress output
RUN mvn dependency:go-offline -pl foundry-server -am -q

# Copy source and build
COPY foundry-storage/src foundry-storage/src
COPY foundry-kvstore/src foundry-kvstore/src
COPY foundry-bitcask/src foundry-bitcask/src
COPY foundry-server/src foundry-server/src
RUN mvn package -pl foundry-server -am -DskipTests -q

# ---- runtime stage ----
# Now a jre-only image (maven is not needed anymore)
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# copy build and deps into new stage
COPY --from=build /app/foundry-server/target/foundry-server-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /app/foundry-server/target/libs libs/

# configure and document exposition of port (actually opening is done by docker-compose or docker run -p)
ENV DATA_DIR=/data
ENV PORT=7070
EXPOSE 7070

# command to run when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]