#!/bin/bash

###############################################################################
# Manual Test Commands for kcat
# Copy and paste individual commands to test specific order types
###############################################################################

# Configuration
BROKER="localhost:9092"
TOPIC="orders.v1"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  Manual Test Commands - Copy & Paste into Terminal            ║"
echo "║  Using kcat at: /opt/homebrew/bin/kcat                        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

cat << 'EOF'

# ============================================================================
# TEST 1: LIMIT ORDER - EXACT MATCH
# ============================================================================

# Send Buy Order
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-1001","timeStamp":"2025-01-10T10:00:00Z","payload":{"orderId":"1001","userId":"user1","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T10:00:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Send Sell Order (Should Match)
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-1002","timeStamp":"2025-01-10T10:00:05Z","payload":{"orderId":"1002","userId":"user2","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T10:00:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: 100 shares executed @ $150.00

# ============================================================================
# TEST 2: MARKET ORDER
# ============================================================================

# Send Limit Sell Order
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-2001","timeStamp":"2025-01-10T10:05:00Z","payload":{"orderId":"2001","userId":"user5","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":200,"limitPrice":149.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T10:05:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Send Market Buy Order (Should Execute @ $149)
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-2002","timeStamp":"2025-01-10T10:05:05Z","payload":{"orderId":"2002","userId":"user6","symbol":"AAPL","side":"BUY","orderType":"MARKET","quantity":150,"timeInForce":"IMMEDIATE_OR_CANCEL","timestamp":"2025-01-10T10:05:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: 150 shares executed @ $149.00

# ============================================================================
# TEST 3: STOP LOSS ORDER
# ============================================================================

# Establish Market Price
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-3001","timeStamp":"2025-01-10T11:00:00Z","payload":{"orderId":"3001","userId":"user9","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T11:00:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

echo '{"eventType":"ORDER_PLACED","correlationId":"evt-3002","timeStamp":"2025-01-10T11:00:05Z","payload":{"orderId":"3002","userId":"user10","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T11:00:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Place Stop Loss Sell @ $148
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-3003","timeStamp":"2025-01-10T11:01:00Z","payload":{"orderId":"3003","userId":"user11","symbol":"AAPL","side":"SELL","orderType":"STOP_MARKET","quantity":100,"stopPrice":148.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T11:01:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Drop Price to Trigger Stop
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-3005","timeStamp":"2025-01-10T11:02:00Z","payload":{"orderId":"3005","userId":"user13","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":147.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T11:02:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

echo '{"eventType":"ORDER_PLACED","correlationId":"evt-3006","timeStamp":"2025-01-10T11:02:05Z","payload":{"orderId":"3006","userId":"user14","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":150,"limitPrice":147.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T11:02:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: Stop loss triggers at $147.50, converts to market order

# ============================================================================
# TEST 4: TRAILING STOP ORDER
# ============================================================================

# Establish Market Price @ $160
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-5001","timeStamp":"2025-01-10T13:00:00Z","payload":{"orderId":"5001","userId":"user22","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":160.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T13:00:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

echo '{"eventType":"ORDER_PLACED","correlationId":"evt-5002","timeStamp":"2025-01-10T13:00:05Z","payload":{"orderId":"5002","userId":"user23","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":160.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T13:00:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Place Trailing Stop with $2 trail (Stop will be $158)
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-5003","timeStamp":"2025-01-10T13:01:00Z","payload":{"orderId":"5003","userId":"user24","symbol":"AAPL","side":"SELL","orderType":"TRAILING_STOP","quantity":100,"trailAmount":2.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T13:01:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Move Price Up to $162 (Stop adjusts to $160)
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-5004","timeStamp":"2025-01-10T13:02:00Z","payload":{"orderId":"5004","userId":"user25","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":50,"limitPrice":162.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T13:02:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

echo '{"eventType":"ORDER_PLACED","correlationId":"evt-5005","timeStamp":"2025-01-10T13:02:05Z","payload":{"orderId":"5005","userId":"user26","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":162.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T13:02:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Drop Price to $159.50 to Trigger
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-5006","timeStamp":"2025-01-10T13:03:00Z","payload":{"orderId":"5006","userId":"user27","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":159.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T13:03:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

echo '{"eventType":"ORDER_PLACED","correlationId":"evt-5007","timeStamp":"2025-01-10T13:03:05Z","payload":{"orderId":"5007","userId":"user28","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":150,"limitPrice":159.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T13:03:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: Trailing stop adjusts from $158 → $160, triggers at $159.50

# ============================================================================
# TEST 5: OCO (ONE-CANCELS-OTHER) ORDER
# ============================================================================

# Establish Market Price @ $170
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-7001","timeStamp":"2025-01-10T15:00:00Z","payload":{"orderId":"7001","userId":"user33","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":170.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T15:00:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

echo '{"eventType":"ORDER_PLACED","correlationId":"evt-7002","timeStamp":"2025-01-10T15:00:05Z","payload":{"orderId":"7002","userId":"user34","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":170.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T15:00:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Place OCO Order: Take Profit @ $172 OR Stop Loss @ $168
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-7003","timeStamp":"2025-01-10T15:01:00Z","payload":{"orderId":"7003","userId":"user35","symbol":"AAPL","side":"SELL","orderType":"OCO","quantity":100,"ocoGroupId":"OCO-GROUP-001","primaryOrderType":"LIMIT","primaryPrice":172.00,"secondaryOrderType":"STOP_MARKET","secondaryStopPrice":168.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T15:01:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Drop Price to Trigger Stop Loss Leg
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-7004","timeStamp":"2025-01-10T15:02:00Z","payload":{"orderId":"7004","userId":"user36","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":167.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T15:02:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

echo '{"eventType":"ORDER_PLACED","correlationId":"evt-7005","timeStamp":"2025-01-10T15:02:05Z","payload":{"orderId":"7005","userId":"user37","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":150,"limitPrice":167.50,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T15:02:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: Stop loss leg triggers, take profit leg automatically cancelled

# ============================================================================
# TEST 6: TIME IN FORCE - IOC (IMMEDIATE OR CANCEL)
# ============================================================================

# IOC Order with Partial Liquidity
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-8001","timeStamp":"2025-01-10T16:00:00Z","payload":{"orderId":"8001","userId":"user40","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":180.00,"timeInForce":"IMMEDIATE_OR_CANCEL","timestamp":"2025-01-10T16:00:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Provide Only 50 shares
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-8002","timeStamp":"2025-01-10T16:00:05Z","payload":{"orderId":"8002","userId":"user41","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":50,"limitPrice":180.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T16:00:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: IOC fills 50 shares, remaining 50 cancelled

# ============================================================================
# TEST 7: TIME IN FORCE - FOK (FILL OR KILL)
# ============================================================================

# FOK Order with Insufficient Liquidity (Should Reject)
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-8003","timeStamp":"2025-01-10T16:01:00Z","payload":{"orderId":"8003","userId":"user42","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":200,"limitPrice":180.00,"timeInForce":"FILL_OR_KILL","timestamp":"2025-01-10T16:01:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Provide Only 150 shares (Insufficient)
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-8004","timeStamp":"2025-01-10T16:01:05Z","payload":{"orderId":"8004","userId":"user43","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":150,"limitPrice":180.00,"timeInForce":"GOOD_TILL_CANCELLED","timestamp":"2025-01-10T16:01:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: FOK order rejected (needs 200, only 150 available)

# FOK Order with Sufficient Liquidity (Should Fill)
echo '{"eventType":"ORDER_PLACED","correlationId":"evt-8005","timeStamp":"2025-01-10T16:02:00Z","payload":{"orderId":"8005","userId":"user44","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":150,"limitPrice":180.00,"timeInForce":"FILL_OR_KILL","timestamp":"2025-01-10T16:02:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Expected: FOK order fills completely (150 shares available)

# ============================================================================
# MONITORING COMMANDS
# ============================================================================

# Watch for trade executions
tail -f logs/application.log | grep "Trade executed"

# Monitor order book state
tail -f logs/application.log | grep -A 20 "ORDER BOOK STATE"

# Check for errors
tail -f logs/application.log | grep -E "ERROR|WARN"

# Check for OCO events
tail -f logs/application.log | grep "OCO"

# Check for stop triggers
tail -f logs/application.log | grep "Stop.*trigger"

# Check trailing stop adjustments
tail -f logs/application.log | grep "Updated trailing stop"

EOF

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║  Commands ready! Copy and paste from above into your terminal  ║"
echo "║  Monitor logs in another terminal window to see results       ║"
echo "╚════════════════════════════════════════════════════════════════╝"
