# How to Run the Application

## Quick Start (Single Command)

Run both backend and frontend together:

```bash
cd "/home/dhruvkothari/Desktop/oop project"
chmod +x install-and-run.sh
./install-and-run.sh
```

This will:
- Install dependencies
- Start backend on port 8080
- Start frontend on port 3000

---

## Manual Setup (Two Terminals)

### Terminal 1 - Backend (Spring Boot)
```bash
cd "/home/dhruvkothari/Desktop/oop project/backend"
mvn clean install
mvn spring-boot:run
```

Backend will be available at: **http://localhost:8080**

### Terminal 2 - Frontend (React + Vite)
```bash
cd "/home/dhruvkothari/Desktop/oop project/frontend"
npm install
npm run dev
```

Frontend will be available at: **http://localhost:3000**

---

## Access the Application

1. **Frontend (Main App)**: http://localhost:3000
2. **Login Page**: http://localhost:3000/login
3. **Backend API**: http://localhost:8080/api
4. **H2 Database Console**: http://localhost:8080/h2-console

### H2 Console Credentials:
- JDBC URL: `jdbc:h2:file:./data/emartDB`
- Username: `sa`
- Password: (leave blank)

---

## First Time Setup

If you haven't installed dependencies yet:

```bash
# Backend dependencies
cd "/home/dhruvkothari/Desktop/oop project/backend"
mvn clean install

# Frontend dependencies
cd "/home/dhruvkothari/Desktop/oop project/frontend"
npm install
```

---

## Troubleshooting

### Port Already in Use
- Backend (8080): Change port in `backend/src/main/resources/application.properties`
- Frontend (3000): Change port in `frontend/vite.config.js`

### Backend Won't Start
- Check Java version: `java -version` (needs Java 21)
- Check Maven: `mvn -version`
- Review backend logs for errors

### Frontend Won't Start
- Check Node.js: `node -v` (needs Node 18+)
- Delete `node_modules` and `package-lock.json`, then run `npm install` again

---

## Testing the Login

1. Open http://localhost:3000/login
2. Click "Register"
3. Fill in:
   - Email: `test@example.com`
   - Password: `test1234`
   - Role: `CUSTOMER`
4. Click "Register"
5. Switch to "Login" mode
6. Enter email and password
7. Click "Login"
8. You'll be redirected to home page with your email displayed

---

## API Endpoints

- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login user
- `POST /api/auth/send-otp` - Send OTP to email
- `POST /api/auth/verify-otp` - Verify OTP code
- `POST /api/auth/social-login` - Social login (OAuth)

---

## Stop the Application

- If using the script: Press `Ctrl+C`
- If running manually: Press `Ctrl+C` in both terminals

