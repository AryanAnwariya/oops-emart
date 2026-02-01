#!/bin/bash

echo "=== Installing prerequisites ==="
echo "Installing Maven and Node.js..."

# Install Maven
if ! command -v mvn &> /dev/null; then
    sudo apt update
    sudo apt install -y maven
fi

# Install Node.js and npm
if ! command -v npm &> /dev/null; then
    curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
    sudo apt-get install -y nodejs
fi

echo ""
echo "=== Installing frontend dependencies ==="
cd frontend
npm install
cd ..

echo ""
echo "=== Installing backend dependencies ==="
cd backend
mvn clean install
cd ..

echo ""
echo "=== Starting services ==="
echo ""
echo "Backend will start on: http://localhost:8080"
echo "Frontend will start on: http://localhost:3000"
echo ""

# Start backend in background
cd backend
mvn spring-boot:run &
BACKEND_PID=$!
cd ..

# Wait a bit for backend to start
sleep 5

# Start frontend
cd frontend
npm run dev &
FRONTEND_PID=$!
cd ..

echo ""
echo "Services started!"
echo "Backend PID: $BACKEND_PID"
echo "Frontend PID: $FRONTEND_PID"
echo ""
echo "Press Ctrl+C to stop all services"
echo ""

# Wait for user interrupt
trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM
wait

