# ---------- Etapa 1: build com Maven ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Dependências primeiro: ficam em cache enquanto o pom.xml não mudar
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---------- Etapa 2: imagem final só com o JRE ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Roda com um usuário sem privilégios
RUN useradd --system --uid 1001 spring
COPY --from=build /app/target/*.jar app.jar
USER spring

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
