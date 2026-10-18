# Stage 1: build the React frontend
FROM node:20-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: build the Spring Boot backend with the frontend bundled in
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN mvn -q clean package -DskipTests

# Stage 3: run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/backend/target/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "java -Xmx400m -Dserver.port=${PORT} -jar app.jar"]
