# Quick Start Guide - Testing Your Refactored OrderBook

## ✅ You Have Everything Ready!

Your `kcat` is installed at: `/opt/homebrew/bin/kcat`

All test files are created and scripts are executable.

## 🚀 Get Started in 3 Steps

### Step 1: Verify Setup

```bash
cd /Users/karirakesh/Documents/Apexon/ExchangeService/kafka-test-payloads
./verify-setup.sh
```

This checks:
- ✓ kcat is installed
- ✓ Kafka broker is accessible
- ✓ Test files are present
- ✓ Application directory exists

### Step 2: Start Your Services

**Terminal 1 - Start Kafka** (if not running):
```bash
# If using Homebrew Kafka
brew services start kafka

# Or manually
cd $KAFKA_HOME
./bin/kafka-server-start.sh config/server.properties
```

**Terminal 2 - Start Exchange Service**:
```bash
cd /Users/karirakesh/Documents/Apexon/ExchangeService
mvn spring-boot:run
```

**Terminal 3 - Monitor Logs**:
```bash
cd /Users/karirakesh/Documents/Apexon/ExchangeService
tail -f logs/application.log
```

### Step 3: Run Tests

**Option A: Automated Test Suite** (Recommended)
```bash
cd /Users/karirakesh/Documents/Apexon/ExchangeService/kafka-test-payloads

# Run all tests
./quick-test.sh all

# Or run specific tests
./quick-test.sh limit      # Limit orders only
./quick-test.sh market     # Market orders only
./quick-test.sh stop       # Stop loss orders
./quick-test.sh trailing   # Trailing stops
./quick-test.sh oco        # OCO orders
./quick-test.sh tif        # Time in force
```

**Option B: Manual Testing** (Copy & Paste)
```bash
# Display manual test commands
./manual-test-commands.sh

# Then copy and paste individual kcat commands from the output
```

**Option C: Single Test Command**
```bash
# Test a simple limit order match
echo '{"eventType":"ORDER_PLACED","eventId":"evt-1001","timestamp":"2025-01-10T10:00:00Z","payload":{"orderId":"1001","userId":"user1","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Wait 2 seconds, then send matching sell order
sleep 2

echo '{"eventType":"ORDER_PLACED","eventId":"evt-1002","timestamp":"2025-01-10T10:00:05Z","payload":{"orderId":"1002","userId":"user2","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P
```

## 📊 What to Watch For

In your log terminal (Terminal 3), you should see:

### Successful Order Processing
```
🧾 Processing OrderPlaced event - OrderId=1001, UserId=user1, Symbol=AAPL, Side=BUY
Adding order: LimitOrder{orderId='1001', side=BUY, price=150.00, quantity=100}
```

### Trade Execution
```
Trade executed: 100 units between Order 1001 (BUY - user1) and Order 1002 (SELL - user2) at price 150.00
```

### Order Book State
```
===== ORDER BOOK STATE =====
BUY Orders:
  <empty or pending orders>
SELL Orders:
  <empty or pending orders>
STOP Orders:
  <stop orders waiting to trigger>
OCO Order Groups:
  <active OCO groups>
Last Traded Price: 150.00
============================
```

### Stop Order Triggers
```
Stop order triggered: StopLossOrder{orderId='3003', stopPrice=148.00}
Converted trailing stop 5003 to market order
```

### OCO Cancellations
```
OCO order detected execution for incoming order: 7003
Cancelling OCO counterpart order: 7003-secondary from group OCO-GROUP-001
```

## 📁 Test Files Overview

| File | Orders | Tests | Time |
|------|--------|-------|------|
| 01-limit-orders.json | 4 | Basic matching | ~10s |
| 02-market-orders.json | 4 | Market execution | ~10s |
| 03-stop-loss-orders.json | 7 | Stop triggers | ~15s |
| 04-stop-limit-orders.json | 6 | Stop-limit conversion | ~15s |
| 05-trailing-stop-orders.json | 7 | Trailing adjustments | ~15s |
| 06-iceberg-orders.json | 4 | Hidden quantity | ~10s |
| 07-oco-orders.json | 7 | OCO cancellation | ~15s |
| 08-time-in-force-tests.json | 7 | IOC/FOK/GTC | ~15s |

**Total:** 46 test orders, ~2 minutes for full suite

## 🔍 Monitoring Commands

```bash
# Watch executions
tail -f logs/application.log | grep "Trade executed"

# Monitor order book
tail -f logs/application.log | grep -A 20 "ORDER BOOK STATE"

# Check errors
tail -f logs/application.log | grep -E "ERROR|WARN"

# OCO events
tail -f logs/application.log | grep "OCO"

# Stop triggers
tail -f logs/application.log | grep -i "trigger"

# Trailing stop adjustments
tail -f logs/application.log | grep "Updated trailing"
```

## ✅ Success Criteria

After running tests, verify:

- [ ] All limit orders match correctly
- [ ] Market orders execute at correct prices
- [ ] Stop orders trigger at expected prices
- [ ] Trailing stops adjust with price movements
- [ ] OCO orders cancel counterparts
- [ ] IOC orders partially fill and cancel remainder
- [ ] FOK orders reject when insufficient liquidity
- [ ] No ERROR logs in application
- [ ] ORDER BOOK STATE shows correct order placement

## 🐛 Troubleshooting

### Problem: "Connection refused" when sending orders

**Solution:**
```bash
# Check if Kafka is running
lsof -i :9092

# If not, start it
brew services start kafka
```

### Problem: Orders sent but not processed

**Solution:**
```bash
# Check if Exchange Service is running
lsof -i :8080

# If not, start it
cd /Users/karirakesh/Documents/Apexon/ExchangeService
mvn spring-boot:run
```

### Problem: "Topic not found"

**Solution:**
```bash
# Check topics
kcat -b localhost:9092 -L

# Create topic manually (optional - auto-created by default)
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic orders.v1 \
  --partitions 1 \
  --replication-factor 1
```

### Problem: Stop orders not triggering

**Cause:** No lastTradedPrice established

**Solution:** Send a limit order pair first to establish price:
```bash
# This establishes lastTradedPrice
echo '{"eventType":"ORDER_PLACED","eventId":"evt-setup1","timestamp":"2025-01-10T10:00:00Z","payload":{"orderId":"setup1","userId":"setupUser","symbol":"AAPL","side":"BUY","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:00Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P

sleep 1

echo '{"eventType":"ORDER_PLACED","eventId":"evt-setup2","timestamp":"2025-01-10T10:00:05Z","payload":{"orderId":"setup2","userId":"setupUser","symbol":"AAPL","side":"SELL","orderType":"LIMIT","quantity":100,"limitPrice":150.00,"timeInForce":"GOOD_TILL_CANCEL","timestamp":"2025-01-10T10:00:05Z"}}' | kcat -b localhost:9092 -t orders.v1 -K: -P
```

## 📚 Documentation

- **README.md** - Detailed usage instructions
- **TEST-SUMMARY.md** - Component validation matrix and benchmarks
- **manual-test-commands.sh** - Copy-paste ready test commands

## 🎯 Next Steps After Testing

1. **Review Logs** - Check all executions completed correctly
2. **Run Performance Tests** - Test with 1000+ orders
3. **Integration Testing** - Connect to real market data
4. **Monitoring Setup** - Configure alerts for errors
5. **Production Deploy** - Deploy refactored components

## 💡 Pro Tips

1. **Run verification first**: Always run `./verify-setup.sh` before testing
2. **Monitor in separate terminal**: Keep logs visible while testing
3. **Test one scenario at a time**: Easier to debug issues
4. **Reset between tests**: Clear order book between test suites
5. **Check timestamps**: Update timestamps to current time for realistic testing

## 🚀 Quick Command Reference

```bash
# Verify everything is ready
./verify-setup.sh

# Run all tests (automated)
./quick-test.sh all

# Run specific test
./quick-test.sh oco

# View manual commands
./manual-test-commands.sh

# Single test order
echo '{...json...}' | kcat -b localhost:9092 -t orders.v1 -K: -P

# Monitor logs
tail -f ../logs/application.log
```

---

## 🎉 You're Ready!

Everything is configured and ready to test your refactored OrderBook.

Start with `./verify-setup.sh` and then run `./quick-test.sh all` to test all components.

Happy Testing! 🚀
