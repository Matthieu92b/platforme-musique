# ===== BUILD =====
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./pom.xml
COPY services ./services
COPY actor-framework ./actor-framework

RUN mvn -pl services/djactor -am clean package -DskipTests

# ===== RUN =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# netcat pour attendre RabbitMQ
RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/services/djactor/target/*.jar app.jar

EXPOSE 8081

CMD sh -c 'until nc -z rabbitmq 5672; do echo "Waiting for rabbitmq:5672..."; sleep 2; done; exec java -jar app.jar'
