# Kafka Test Payloads for OrderBook Testing

This directory contains comprehensive Kafka payload examples for testing all order types in the refactored ExchangeService.

## Overview

The test payloads are organized by order type and scenario, covering:

1. **Limit Orders** - Basic buy/sell with exact match, partial fill, price improvement
2. **Market Orders** - Market orders matching with limit orders
3. **Stop Loss Orders** - Stop market orders that trigger on price movements
4. **Stop Limit Orders** - Stop orders that convert to limit orders when triggered
5. **Trailing Stop Orders** - Dynamic stop orders that adjust with favorable price movement
6. **Iceberg Orders** - Large orders with hidden quantity
7. **OCO Orders** - One-Cancels-Other orders with two legs
8. **Time In Force** - Testing IOC, FOK, GTC order behaviors

## Prerequisites

- Kafka broker running (typically at `localhost:9092`)
- Exchange Service running and listening to `orders.v1` topic
- Kafka command-line tools or a Kafka client like `kafkacat` or Kafka UI

## How to Send Test Payloads

### Method 1: Using kafka-console-producer

```bash
# Navigate to Kafka installation directory
cd $KAFKA_HOME/bin

# Send a single order
echo '{
  "eventType": "ORDER_PLACED",
  "eventId": "evt-1001",
  "timestamp": "2025-01-10T10:00:00Z",
  "payload": {
    "orderId": "1001",
    "userId": "user1",
    "symbol": "AAPL",
    "side": "BUY",
    "orderType": "LIMIT",
    "quantity": 100,
    "limitPrice": 150.00,
    "timeInForce": "GOOD_TILL_CANCEL",
    "timestamp": "2025-01-10T10:00:00Z"
  }
}' | ./kafka-console-producer.sh \
  --broker-list localhost:9092 \
  --topic orders.v1 \
  --property "key.separator=:" \
  --property "parse.key=true" <<< "ORDER_1001:{json}"
```

### Method 2: Using kcat/kafkacat (recommended)

```bash
# Install kcat (if not already installed)
# macOS: brew install kcat
# Ubuntu: apt-get install kafkacat

# Send order with key (using kcat - newer name)
echo '{"eventType":"ORDER_PLACED","eventId":"evt-1001","timestamp":"2025-01-10T10:00:00Z","payload":{"orderId":"1001","userId":"user1","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:00Z"}}' | \
kcat -b localhost:9092 -t orders.v1 -K: -P <<< "ORDER_1001:{json}"

# Or using kafkacat (older name, same tool)
echo '{"eventType":"ORDER_PLACED","eventId":"evt-1001","timestamp":"2025-01-10T10:00:00Z","payload":{"orderId":"1001","userId":"user1","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:00Z"}}' | \
kafkacat -b localhost:9092 -t orders.v1 -K: -P <<< "ORDER_1001:{json}"
```

### Method 3: Using a Script (Bulk Testing)

Create a shell script `send-test-orders.sh`:

```bash
#!/bin/bash

# Set Kafka broker
BROKER="localhost:9092"
TOPIC="orders.v1"

# Function to send order
send_order() {
  local key=$1
  local payload=$2
  echo "$payload" | kcat -b $BROKER -t $TOPIC -K: -P <<< "$key:$payload"
  echo "Sent order: $key"
  sleep 1  # Wait 1 second between orders
}

# Example: Send limit orders
send_order "ORDER_1001" '{"eventType":"ORDER_PLACED","eventId":"evt-1001","timestamp":"2025-01-10T10:00:00Z","payload":{"orderId":"1001","userId":"user1","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:00Z"}}'

send_order "ORDER_1002" '{"eventType":"ORDER_PLACED","eventId":"evt-1002","timestamp":"2025-01-10T10:00:05Z","payload":{"orderId":"1002","userId":"user2","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:05Z"}}'
```

## Test Scenarios

### Scenario 1: Basic Limit Order Matching

**File:** `01-limit-orders.json`

**Test Steps:**
1. Send ORDER_1001 (Buy 100 @ $150)
2. Send ORDER_1002 (Sell 100 @ $150)
3. **Expected:** Complete match, 100 shares executed @ $150

### Scenario 2: Market Order Execution

**File:** `02-market-orders.json`

**Test Steps:**
1. Send ORDER_2001 (Limit Sell 200 @ $149)
2. Send ORDER_2002 (Market Buy 150)
3. **Expected:** Market buy fills 150 shares @ $149

### Scenario 3: Stop Loss Trigger

**File:** `03-stop-loss-orders.json`

**Test Steps:**
1. Establish market price with ORDER_3001 & ORDER_3002
2. Place stop loss ORDER_3003 (Stop @ $148)
3. Send orders to drop price below $148
4. **Expected:** Stop loss triggers and converts to market order

### Scenario 4: Stop Limit Conversion

**File:** `04-stop-limit-orders.json`

**Test Steps:**
1. Establish market price
2. Place stop limit ORDER_4003 (Stop @ $157, Limit @ $158)
3. Trigger with price movement above $157
4. **Expected:** Converts to limit order @ $158

### Scenario 5: Trailing Stop Adjustment

**File:** `05-trailing-stop-orders.json`

**Test Steps:**
1. Establish market price @ $160
2. Place trailing stop ORDER_5003 (Trail $2)
3. Move price up to $162
4. **Expected:** Stop adjusts from $158 to $160
5. Drop price below $160
6. **Expected:** Trailing stop triggers

### Scenario 6: Iceberg Order Execution

**File:** `06-iceberg-orders.json`

**Test Steps:**
1. Place iceberg ORDER_6001 (500 total, 100 display)
2. Send multiple sell orders
3. **Expected:** Only 100 visible at a time, gradually reveals

### Scenario 7: OCO Order Execution

**File:** `07-oco-orders.json`

**Test Steps:**
1. Establish market price
2. Place OCO ORDER_7003 (Take Profit @ $172 OR Stop @ $168)
3. Trigger one leg
4. **Expected:** Other leg automatically cancelled

### Scenario 8: Time In Force Behaviors

**File:** `08-time-in-force-tests.json`

**Test Steps:**
1. Send IOC order with partial liquidity
2. **Expected:** Partial fill, remainder cancelled
3. Send FOK order with insufficient liquidity
4. **Expected:** Order rejected entirely
5. Send FOK order with sufficient liquidity
6. **Expected:** Complete fill

## Monitoring Test Results

### Check Application Logs

```bash
# Tail the application logs
tail -f /path/to/exchange-service/logs/application.log

# Look for execution messages
grep "Trade executed" /path/to/exchange-service/logs/application.log

# Check for OCO cancellations
grep "OCO" /path/to/exchange-service/logs/application.log
```

### Verify Order Book State

The logs should show order book state after each order:
```
===== ORDER BOOK STATE =====
BUY Orders: ...
SELL Orders: ...
STOP Orders: ...
OCO Order Groups: ...
Last Traded Price: ...
============================
```

## Common Issues & Troubleshooting

### Issue 1: Orders Not Processing

**Problem:** Kafka messages sent but no logs in application

**Solution:**
- Check Kafka consumer group: `kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group exchange-service --describe`
- Verify topic exists: `kafka-topics.sh --bootstrap-server localhost:9092 --list`
- Check application.yml Kafka configuration

### Issue 2: JSON Parsing Errors

**Problem:** "Error parsing JSON message" in logs

**Solution:**
- Validate JSON format using `jq`: `echo '{"your":"json"}' | jq`
- Ensure all required fields are present
- Check timestamp format (ISO 8601)

### Issue 3: Order Validation Failures

**Problem:** "[VALIDATION ERROR]" in logs

**Solution:**
- Check order constraints (quantity > 0, price > 0)
- Verify TimeInForce value is valid
- Ensure stop price relationships are correct

## Expected Test Results Summary

| Test File | Orders | Expected Executions | Key Validations |
|-----------|--------|---------------------|-----------------|
| 01-limit-orders.json | 4 | 2 trades | Price improvement, FIFO |
| 02-market-orders.json | 4 | 2 trades | Market price execution |
| 03-stop-loss-orders.json | 7 | 3 trades | Stop trigger @ $147.50 |
| 04-stop-limit-orders.json | 6 | 2 trades | Stop converts to limit |
| 05-trailing-stop-orders.json | 7 | 4 trades | Stop adjusts with price |
| 06-iceberg-orders.json | 4 | 3 trades | Gradual quantity reveal |
| 07-oco-orders.json | 7 | 4 trades | Leg cancellation |
| 08-time-in-force-tests.json | 7 | 3 trades | IOC/FOK behaviors |

## Advanced Testing

### Load Testing

Run multiple orders in parallel:

```bash
for i in {1..100}; do
  ORDER_ID="LOAD_$i"
  send_order "$ORDER_ID" "{...payload...}" &
done
wait
```

### Stress Testing Order Matching

Send rapid-fire orders to test the refactored components:

```bash
# Send 1000 orders as fast as possible
seq 1 1000 | xargs -P 10 -I {} sh -c 'send_order "ORDER_{}" "{...}"'
```

## Notes

- All timestamps should be updated to current time for realistic testing
- Order IDs must be unique across all tests
- Symbol "AAPL" is used consistently; change if needed
- Test payloads assume clean order book state; reset between test suites
- OCO orders require special handling - see ORDER_7003 and ORDER_7006 examples

## Support

For issues with the refactored OrderBook or test payloads, check:
- Application logs: `logs/application.log`
- Kafka consumer lag: `kafka-consumer-groups.sh`
- Order validation rules: `OrderEventValidator.java`
- Matching engine logic: Refactored components in `utils/` package
