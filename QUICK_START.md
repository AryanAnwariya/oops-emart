# Quick Start Commands

## Run these commands in your terminal:

### 1. Install prerequisites (one-time setup):
```bash
sudo apt update
sudo apt install -y maven
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs
```

### 2. Install dependencies:
```bash
# Install frontend dependencies
cd /home/dhruvkothari/OOPS-emart/frontend
npm install

# Install backend dependencies
cd /home/dhruvkothari/OOPS-emart/backend
mvn clean install
```

### 3. Start the services:

**In Terminal 1 - Start Backend:**
```bash
cd /home/dhruvkothari/OOPS-emart/backend
mvn spring-boot:run
```

**In Terminal 2 - Start Frontend:**
```bash
cd /home/dhruvkothari/OOPS-emart/frontend
npm run dev
```

### Access the application:
- Backend: http://localhost:8080
- Frontend: http://localhost:3000
- Health Check: http://localhost:8080/actuator/health

---

**Or use the all-in-one script:**
```bash
cd /home/dhruvkothari/OOPS-emart
./install-and-run.sh
```

