#!/bin/bash

# Hermes Android Bridge - Quick Start Script
# This script sets up and starts both Python server and verifies installation

set -e

echo "========================================="
echo "🚀 Hermes Android Bridge Setup"
echo "========================================="

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() { echo -e "${YELLOW}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Step 1: Check Python version
print_info "Step 1/4: Checking Python installation..."
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version)
    print_success "$PYTHON_VERSION found"
elif command -v python &> /dev/null; then
    PYTHON_VERSION=$(python --version)
    print_success "$PYTHON_VERSION found"
else
    print_error "Python not found. Please install Python 3.8 or higher."
    exit 1
fi

# Step 2: Install Python dependencies
print_info "Step 2/4: Installing Python dependencies..."
cd "$(dirname "$0")/python-relay-server"

# Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    print_info "Creating virtual environment..."
    python3 -m venv venv
fi

# Activate venv
source venv/bin/activate

# Install requirements
print_info "Installing packages from requirements.txt..."
pip install -q -r requirements.txt
print_success "Dependencies installed"

# Step 3: Verify Python script works
print_info "Step 3/4: Testing Python script syntax..."
if python3 -m py_compile relay_server.py; then
    print_success "Syntax check passed"
else
    print_error "Python script has syntax errors"
    exit 1
fi

# Step 4: Start the server
print_info "Step 4/4: Starting relay server..."

echo ""
echo "========================================="
echo "Server starting in background..."
echo "WebSocket endpoint: ws://localhost:8765/ws"
echo "Health check: http://localhost:8765/"
echo "Press Ctrl+C to stop server"
echo "========================================="
echo ""

# Run server in foreground (or uncomment next line for background)
exec python3 relay_server.py --host 0.0.0.0 --port 8765

# Uncomment this line to run in background instead:
# nohup python3 relay_server.py --host 0.0.0.0 --port 8765 > server.log 2>&1 &
