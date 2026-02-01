# E-Mart Backend

Spring Boot backend application for E-Mart

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL database

## Setup Instructions

1. Install Maven (if not already installed):
   ```bash
   sudo apt install maven
   ```

2. Install PostgreSQL (if not already installed):
   ```bash
   sudo apt install postgresql postgresql-contrib
   ```

3. Create database:
   ```bash
   sudo -u postgres psql
   CREATE DATABASE emart;
   \q
   ```

4. Build the project:
   ```bash
   cd backend
   mvn clean install
   ```

5. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The application will be available at http://localhost:8080

## API Endpoints

- Health Check: http://localhost:8080/actuator/health

## Technology Stack

- Spring Boot 3.2.0
- Spring Data JPA
- PostgreSQL
- Spring Actuator

