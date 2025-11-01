### run a service (example with djactor) from root
 ./mvnw spring-boot:run -pl services/djactor

### run rabbitmq (install docker before)
docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management