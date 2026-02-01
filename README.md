# E-Mart Project

A full-stack e-commerce application with Spring Boot backend and React frontend.

## Project Structure

```
OOPS-emart/
├── backend/          # Spring Boot Backend
├── frontend/         # React Frontend
└── README.md
```

## Quick Start

### Backend Setup

```bash
# Navigate to backend directory
cd backend

# Install Java 17 and Maven
sudo apt install openjdk-21-jdk maven

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

Backend will be available at: http://localhost:8080

### Frontend Setup

```bash
# Navigate to frontend directory
cd frontend

# Install Node.js and npm (if not installed)
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Install dependencies
npm install

# Run the development server
npm run dev
```

Frontend will be available at: http://localhost:3000

## Development

### Backend
- **Port**: 8080
- **Database**: PostgreSQL (localhost:5432)
- **API Base**: http://localhost:8080/api

### Frontend
- **Port**: 3000
- **Build Tool**: Vite
- **Framework**: React 18

## Prerequisites

- Java 17+
- Maven 3.6+
- Node.js 18+
- PostgreSQL

## Installation Commands

### Install Java 21
```bash
sudo apt update
sudo apt install openjdk-21-jdk
java -version
```

### Install Maven
```bash
sudo apt install maven
mvn -version
```

### Install Node.js and npm
```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs
node -v
npm -v
```

### Install PostgreSQL
```bash
sudo apt install postgresql postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### Setup PostgreSQL Database
```bash
sudo -u postgres psql
CREATE DATABASE emart;
CREATE USER emart_user WITH PASSWORD 'emart_password';
GRANT ALL PRIVILEGES ON DATABASE emart TO emart_user;
\q
```

## Features

- **Multi-role Authentication**: Customer, Retailer, Wholesaler roles
- **Social Login**: Google and Facebook OAuth integration
- **Product Management**: CRUD operations for products
- **Image Upload**: Product image support
- **Role-based Dashboards**: Different interfaces for different user types
- **OTP Verification**: Email-based OTP authentication

## Social Login Setup

The application supports Google and Facebook social login. To set up:

1. Copy `frontend/.env.example` to `frontend/.env`
2. Configure your OAuth credentials:
   ```
   VITE_GOOGLE_CLIENT_ID=your-google-client-id
   VITE_FACEBOOK_APP_ID=your-facebook-app-id
   ```
3. Follow the detailed setup guide in `SOCIAL_LOGIN_SETUP.md`

## API Documentation

- Health Check: http://localhost:8080/actuator/health
- Actuator: http://localhost:8080/actuator
- Auth Endpoints: http://localhost:8080/api/auth/*

## Build Commands

### Build Backend
```bash
cd backend
mvn clean package
```

### Build Frontend
```bash
cd frontend
npm run build
```

## Run Production

### Backend
```bash
cd backend
java -jar target/backend-1.0.0.jar
```

### Frontend
```bash
cd frontend
npm run preview
```

