FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/springboot/pom.xml backend/springboot/pom.xml
RUN mvn -q -f backend/springboot/pom.xml dependency:go-offline
COPY backend/springboot/src backend/springboot/src
COPY frontend frontend
RUN mvn -q -f backend/springboot/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/springboot/target/serialposition-0.0.1-SNAPSHOT.jar app.jar
CMD ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]
