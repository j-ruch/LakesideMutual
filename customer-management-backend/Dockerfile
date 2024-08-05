FROM openjdk:17-slim-buster as build

COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

RUN ./mvnw -B dependency:go-offline                          

COPY src src

RUN ./mvnw -B package                                        

FROM openjdk:17-slim-buster

COPY --from=build target/customer-management-backend-0.0.1-SNAPSHOT.jar .

RUN mkdir -p /usr/local/openjdk-17/conf/management && touch /usr/local/openjdk-17/conf/management/jmxremote.password
RUN chmod 600 /usr/local/openjdk-17/conf/management/jmxremote.password

EXPOSE 8100
EXPOSE 8850

CMD java \
 -Dcom.sun.management.jmxremote=true \
 -Dcom.sun.management.jmxremote.local.only=false \
 -Dcom.sun.management.jmxremote.authenticate=false \
 -Dcom.sun.management.jmxremote.ssl=false \
 -Djava.rmi.server.hostname=localhost \
 -Dcom.sun.management.jmxremote.port=8850 \
 -Dcom.sun.management.jmxremote.rmi.port=8850 \
 -jar customer-management-backend-0.0.1-SNAPSHOT.jar