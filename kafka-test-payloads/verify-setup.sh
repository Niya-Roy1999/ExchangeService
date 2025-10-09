#!/bin/bash

###############################################################################
# Setup Verification Script
# Checks if all prerequisites are met for testing
###############################################################################

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║  Exchange Service - Test Setup Verification                   ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
}

check_pass() {
    echo -e "${GREEN}✓${NC} $1"
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
}

check_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

# Counters
PASSED=0
FAILED=0
WARNED=0

print_header

# Check 1: kcat/kafkacat
echo "Checking Kafka client tools..."
if command -v kcat &> /dev/null; then
    KCAT_PATH=$(which kcat)
    check_pass "kcat found at: $KCAT_PATH"
    ((PASSED++))
elif command -v kafkacat &> /dev/null; then
    KAFKACAT_PATH=$(which kafkacat)
    check_pass "kafkacat found at: $KAFKACAT_PATH"
    ((PASSED++))
else
    check_fail "kcat/kafkacat not found"
    check_info "Install with: brew install kcat (macOS)"
    ((FAILED++))
fi
echo ""

# Check 2: Kafka broker
echo "Checking Kafka broker..."
BROKER="${KAFKA_BROKER:-localhost:9092}"
if command -v kcat &> /dev/null || command -v kafkacat &> /dev/null; then
    CLIENT=$(command -v kcat || command -v kafkacat)
    if gtimeout 5 $CLIENT -b $BROKER -L &> /dev/null 2>&1 || $CLIENT -b $BROKER -L &> /dev/null; then
        check_pass "Kafka broker accessible at: $BROKER"
        ((PASSED++))
    else
        check_fail "Cannot connect to Kafka broker at: $BROKER"
        check_info "Start Kafka or check KAFKA_BROKER environment variable"
        ((FAILED++))
    fi
else
    check_warn "Cannot verify Kafka broker (kcat not found)"
    ((WARNED++))
fi
echo ""

# Check 3: Kafka topic
echo "Checking Kafka topic 'orders.v1'..."
TOPIC="orders.v1"
if command -v kcat &> /dev/null || command -v kafkacat &> /dev/null; then
    CLIENT=$(command -v kcat || command -v kafkacat)
    if $CLIENT -b $BROKER -L 2>/dev/null | grep -q "$TOPIC"; then
        check_pass "Topic '$TOPIC' exists"
        ((PASSED++))
    else
        check_warn "Topic '$TOPIC' not found (may be auto-created)"
        check_info "Topic will be created automatically when first message is sent"
        ((WARNED++))
    fi
else
    check_warn "Cannot verify topic (kcat not found)"
    ((WARNED++))
fi
echo ""

# Check 4: Java/Maven
echo "Checking Java environment..."
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    check_pass "Java found: $JAVA_VERSION"
    ((PASSED++))
else
    check_fail "Java not found"
    check_info "Install Java JDK 17 or higher"
    ((FAILED++))
fi

if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -n 1)
    check_pass "Maven found: $MVN_VERSION"
    ((PASSED++))
else
    check_warn "Maven not found"
    check_info "Maven needed to build/run the application"
    ((WARNED++))
fi
echo ""

# Check 5: Application directory
echo "Checking application files..."
APP_DIR="/Users/karirakesh/Documents/Apexon/ExchangeService"
if [ -d "$APP_DIR" ]; then
    check_pass "Application directory found: $APP_DIR"
    ((PASSED++))

    if [ -f "$APP_DIR/pom.xml" ]; then
        check_pass "Maven project file (pom.xml) found"
        ((PASSED++))
    else
        check_fail "pom.xml not found"
        ((FAILED++))
    fi

    if [ -f "$APP_DIR/src/main/java/com/example/ExchangeService/ExchangeService/utils/OrderBook.java" ]; then
        check_pass "Refactored OrderBook.java found"
        ((PASSED++))
    else
        check_fail "OrderBook.java not found"
        ((FAILED++))
    fi
else
    check_fail "Application directory not found: $APP_DIR"
    ((FAILED++))
fi
echo ""

# Check 6: Test payload files
echo "Checking test payload files..."
TEST_DIR="/Users/karirakesh/Documents/Apexon/ExchangeService/kafka-test-payloads"
if [ -d "$TEST_DIR" ]; then
    check_pass "Test payloads directory found"
    ((PASSED++))

    PAYLOAD_COUNT=$(ls -1 "$TEST_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')
    if [ "$PAYLOAD_COUNT" -eq 8 ]; then
        check_pass "All 8 test payload files present"
        ((PASSED++))
    else
        check_warn "Found $PAYLOAD_COUNT test files (expected 8)"
        ((WARNED++))
    fi

    if [ -x "$TEST_DIR/quick-test.sh" ]; then
        check_pass "quick-test.sh is executable"
        ((PASSED++))
    else
        check_warn "quick-test.sh not executable"
        check_info "Run: chmod +x $TEST_DIR/quick-test.sh"
        ((WARNED++))
    fi
else
    check_fail "Test payloads directory not found"
    ((FAILED++))
fi
echo ""

# Check 7: Application running
echo "Checking if Exchange Service is running..."
if lsof -i :8080 &> /dev/null; then
    check_pass "Application appears to be running on port 8080"
    ((PASSED++))
else
    check_warn "Application not running on port 8080"
    check_info "Start with: cd $APP_DIR && mvn spring-boot:run"
    ((WARNED++))
fi
echo ""

# Summary
echo "════════════════════════════════════════════════════════════════"
echo "                        VERIFICATION SUMMARY"
echo "════════════════════════════════════════════════════════════════"
echo -e "${GREEN}Passed:${NC}  $PASSED"
echo -e "${YELLOW}Warnings:${NC} $WARNED"
echo -e "${RED}Failed:${NC}  $FAILED"
echo "════════════════════════════════════════════════════════════════"
echo ""

# Final verdict
if [ $FAILED -eq 0 ]; then
    if [ $WARNED -eq 0 ]; then
        echo -e "${GREEN}✓ All checks passed! You're ready to run tests.${NC}"
        echo ""
        echo "Next steps:"
        echo "  1. Start Exchange Service (if not running):"
        echo "     cd $APP_DIR && mvn spring-boot:run"
        echo ""
        echo "  2. Run automated tests:"
        echo "     cd $TEST_DIR && ./quick-test.sh all"
        echo ""
        echo "  3. Or use manual commands:"
        echo "     ./manual-test-commands.sh"
    else
        echo -e "${YELLOW}⚠ Setup mostly complete with $WARNED warning(s).${NC}"
        echo "Review warnings above and fix if needed."
    fi
else
    echo -e "${RED}✗ Setup incomplete. Please fix $FAILED failed check(s) above.${NC}"
    exit 1
fi

echo ""
