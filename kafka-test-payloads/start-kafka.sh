#!/bin/bash

###############################################################################
# Start Kafka (KRaft mode - no Zookeeper needed)
# Works with Homebrew installations of Kafka 3.x+
###############################################################################

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  Kafka KRaft Startup Script                                    ║"
echo "║  (No Zookeeper required)                                       ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Check if Kafka is already running
if lsof -i :9092 &> /dev/null; then
    print_success "Kafka is already running on port 9092"
    print_info "Kafka broker is ready for testing"

    # Verify it's responding
    if command -v kcat &> /dev/null; then
        if kcat -b localhost:9092 -L &> /dev/null; then
            print_success "Kafka broker is responding correctly"
            exit 0
        else
            print_warn "Port 9092 is in use but Kafka may not be responding"
            print_info "Try restarting: brew services restart kafka"
        fi
    fi
    exit 0
fi

print_info "Kafka is not running. Starting Kafka in KRaft mode..."

# Start Kafka via Homebrew
if command -v brew &> /dev/null; then
    print_info "Starting Kafka via Homebrew (KRaft mode)..."
    brew services start kafka

    # Wait for Kafka to start
    print_info "Waiting for Kafka to start (this may take 10-20 seconds)..."
    for i in {1..40}; do
        if lsof -i :9092 &> /dev/null; then
            # Give it a moment to fully initialize
            sleep 2

            # Verify Kafka is responding
            if command -v kcat &> /dev/null; then
                if kcat -b localhost:9092 -L &> /dev/null; then
                    print_success "Kafka started successfully on port 9092 (KRaft mode)"
                    echo ""
                    print_info "✓ Kafka is ready for testing!"
                    print_info "✓ No Zookeeper needed (using KRaft)"
                    print_info ""
                    print_info "Next steps:"
                    print_info "  1. Run verification: ./verify-setup.sh"
                    print_info "  2. Start Exchange Service in another terminal"
                    print_info "  3. Run tests: ./quick-test.sh all"
                    exit 0
                fi
            else
                print_success "Kafka started on port 9092"
                print_warn "Cannot verify with kcat (not installed)"
                exit 0
            fi
        fi
        sleep 1
        printf "."
    done

    echo ""
    print_error "Kafka did not start within 40 seconds"
    echo ""
    print_info "Troubleshooting steps:"
    print_info "  1. Check Kafka status: brew services list"
    print_info "  2. Check Kafka logs: tail -f /opt/homebrew/var/log/kafka/server.log"
    print_info "  3. Restart Kafka: brew services restart kafka"
    print_info "  4. Check port: lsof -i :9092"
    exit 1
else
    print_error "Homebrew not found"
    echo ""
    print_info "To start Kafka manually in KRaft mode:"
    echo ""
    echo "  # Generate cluster UUID (first time only)"
    echo "  KAFKA_CLUSTER_ID=\"\$(kafka-storage random-uuid)\""
    echo ""
    echo "  # Format storage (first time only)"
    echo "  kafka-storage format -t \$KAFKA_CLUSTER_ID -c /opt/homebrew/etc/kafka/kraft/server.properties"
    echo ""
    echo "  # Start Kafka"
    echo "  kafka-server-start /opt/homebrew/etc/kafka/kraft/server.properties"
    echo ""
    exit 1
fi
