# ---- Etapa de build ----
# El Maven Wrapper fija la versión de Maven (.mvn/wrapper/maven-wrapper.properties).
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn ./.mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- Etapa de runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/target/delivery-service.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
