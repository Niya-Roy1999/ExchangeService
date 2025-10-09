#!/bin/bash

#############################################
# Quick Test Script for Exchange Service
# Tests all order types in the refactored OrderBook
#############################################

# Configuration
BROKER="${KAFKA_BROKER:-localhost:9092}"
TOPIC="${KAFKA_TOPIC:-orders.v1}"
DELAY="${ORDER_DELAY:-2}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to send order to Kafka
send_order() {
    local key=$1
    local payload=$2
    local description=$3

    print_info "Sending $description (Key: $key)"

    if command -v kcat &> /dev/null; then
        echo "$payload" | kcat -b $BROKER -t $TOPIC -K: -P <<< "$key:$payload"
        if [ $? -eq 0 ]; then
            print_success "Order sent successfully"
        else
            print_error "Failed to send order"
            return 1
        fi
    elif command -v kafkacat &> /dev/null; then
        echo "$payload" | kafkacat -b $BROKER -t $TOPIC -K: -P <<< "$key:$payload"
        if [ $? -eq 0 ]; then
            print_success "Order sent successfully"
        else
            print_error "Failed to send order"
            return 1
        fi
    else
        print_error "kcat/kafkacat not found. Please install it: brew install kcat (macOS) or apt-get install kafkacat (Linux)"
        exit 1
    fi

    sleep $DELAY
}

# Function to run test scenario
run_scenario() {
    local scenario_name=$1
    echo ""
    echo "=========================================="
    print_info "Starting: $scenario_name"
    echo "=========================================="
}

# Test 1: Limit Order Basic Matching
test_limit_orders() {
    run_scenario "Test 1: Limit Order Basic Matching"

    send_order "ORDER_1001" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-1001","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"1001","userId":"user1","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Buy 100 @ \$150"

    send_order "ORDER_1002" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-1002","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"1002","userId":"user2","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 100 @ \$150 (Should Match)"

    print_success "Expected: 100 shares executed @ \$150.00"
}

# Test 2: Market Orders
test_market_orders() {
    run_scenario "Test 2: Market Order Execution"

    send_order "ORDER_2001" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-2001","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"2001","userId":"user5","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":200,"limitPrice":149.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 200 @ \$149"

    send_order "ORDER_2002" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-2002","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"2002","userId":"user6","symbol":"AAPL","side":"BUY","orderType":"MARKET","quantity":150,"timeInForce":"IMMEDIATE_OR_CANCEL","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Market Buy 150 (Should Execute @ \$149)"

    print_success "Expected: 150 shares executed @ \$149.00"
}

# Test 3: Stop Loss Orders
test_stop_loss() {
    run_scenario "Test 3: Stop Loss Order Trigger"

    # Establish market price
    send_order "ORDER_3001" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-3001","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"3001","userId":"user9","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Buy 100 @ \$150"

    send_order "ORDER_3002" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-3002","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"3002","userId":"user10","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 100 @ \$150 (Set lastTradedPrice)"

    # Place stop loss
    send_order "ORDER_3003" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-3003","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"3003","userId":"user11","symbol":"AAPL","side":"SELL","orderType":"STOP_MARKET","quantity":100,"stopPrice":148.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Stop Loss Sell @ \$148"

    # Drop price to trigger
    send_order "ORDER_3005" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-3005","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"3005","userId":"user13","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":147.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 50 @ \$147.50"

    send_order "ORDER_3006" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-3006","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"3006","userId":"user14","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":150,"limitPrice":147.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Buy 150 @ \$147.50 (Trigger Stop)"

    print_success "Expected: Stop loss triggers when price drops to \$147.50"
}

# Test 4: Trailing Stop
test_trailing_stop() {
    run_scenario "Test 4: Trailing Stop Order"

    # Establish price
    send_order "ORDER_5001" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-5001","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"5001","userId":"user22","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":160.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Buy 100 @ \$160"

    send_order "ORDER_5002" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-5002","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"5002","userId":"user23","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":160.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 100 @ \$160"

    # Place trailing stop
    send_order "ORDER_5003" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-5003","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"5003","userId":"user24","symbol":"AAPL","side":"SELL","orderType":"TRAILING_STOP","quantity":100,"trailAmount":2.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Trailing Stop Sell with \$2 trail"

    # Move price up
    send_order "ORDER_5004" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-5004","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"5004","userId":"user25","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":50,"limitPrice":162.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Buy 50 @ \$162"

    send_order "ORDER_5005" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-5005","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"5005","userId":"user26","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":162.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 50 @ \$162 (Stop adjusts to \$160)"

    print_success "Expected: Trailing stop adjusts from \$158 to \$160"
}

# Test 5: OCO Orders
test_oco_orders() {
    run_scenario "Test 5: OCO (One-Cancels-Other) Orders"

    # Establish price
    send_order "ORDER_7001" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-7001","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"7001","userId":"user33","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":170.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Buy 100 @ \$170"

    send_order "ORDER_7002" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-7002","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"7002","userId":"user34","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":170.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 100 @ \$170"

    # Place OCO order
    send_order "ORDER_7003" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-7003","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"7003","userId":"user35","symbol":"AAPL","side":"SELL","orderType":"OCO","quantity":100,"ocoGroupId":"OCO-GROUP-001","primaryOrderType":"LIMIT","primaryPrice":172.00,"secondaryOrderType":"STOP_MARKET","secondaryStopPrice":168.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "OCO: Take Profit @ \$172 OR Stop Loss @ \$168"

    # Drop price to trigger stop loss leg
    send_order "ORDER_7004" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-7004","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"7004","userId":"user36","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":167.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 50 @ \$167.50"

    send_order "ORDER_7005" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-7005","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"7005","userId":"user37","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":150,"limitPrice":167.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Buy 150 @ \$167.50 (Trigger Stop Loss leg)"

    print_success "Expected: Stop loss leg triggers, take profit leg cancelled"
}

# Test 6: Time In Force
test_time_in_force() {
    run_scenario "Test 6: Time In Force (IOC & FOK)"

    # IOC test
    send_order "ORDER_8001" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-8001","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"8001","userId":"user40","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":180.00,"timeInForce":"IMMEDIATE_OR_CANCEL","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "IOC Buy 100 @ \$180"

    send_order "ORDER_8002" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-8002","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"8002","userId":"user41","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":180.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 50 @ \$180 (Partial fill IOC)"

    print_success "Expected: IOC fills 50 shares, remaining 50 cancelled"

    # FOK test - insufficient liquidity
    send_order "ORDER_8003" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-8003","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"8003","userId":"user42","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":200,"limitPrice":180.00,"timeInForce":"FILL_OR_KILL","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "FOK Buy 200 @ \$180"

    send_order "ORDER_8004" \
    '{"eventType":"ORDER_PLACED","correlationId":"evt-8004","timeStamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","payload":{"orderId":"8004","userId":"user43","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":150,"limitPrice":180.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"}}' \
    "Limit Sell 150 @ \$180 (Insufficient for FOK)"

    print_success "Expected: FOK order rejected (only 150 available, needs 200)"
}

# Main execution
main() {
    echo ""
    echo "╔════════════════════════════════════════════╗"
    echo "║  Exchange Service - Quick Test Suite      ║"
    echo "║  Testing Refactored OrderBook Components  ║"
    echo "╚════════════════════════════════════════════╝"
    echo ""

    print_info "Kafka Broker: $BROKER"
    print_info "Topic: $TOPIC"
    print_info "Delay between orders: ${DELAY}s"
    echo ""

    read -p "Press Enter to start tests (or Ctrl+C to cancel)..."

    # Run all tests
    test_limit_orders
    test_market_orders
    test_stop_loss
    test_trailing_stop
    test_oco_orders
    test_time_in_force

    echo ""
    echo "╔════════════════════════════════════════════╗"
    echo "║         All Tests Completed!               ║"
    echo "╚════════════════════════════════════════════╝"
    echo ""
    print_info "Check application logs for execution details"
    print_info "Look for 'ORDER BOOK STATE' messages to verify matching"
}

# Check if running specific test
case "${1:-all}" in
    limit)
        test_limit_orders
        ;;
    market)
        test_market_orders
        ;;
    stop)
        test_stop_loss
        ;;
    trailing)
        test_trailing_stop
        ;;
    oco)
        test_oco_orders
        ;;
    tif)
        test_time_in_force
        ;;
    all)
        main
        ;;
    *)
        echo "Usage: $0 {all|limit|market|stop|trailing|oco|tif}"
        exit 1
        ;;
esac
