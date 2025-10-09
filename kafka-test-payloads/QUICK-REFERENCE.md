# Quick Reference Card 🎯

## Start Testing NOW

### Terminal 1: Start Application
```bash
cd /Users/karirakesh/Documents/Apexon/ExchangeService
mvn spring-boot:run
```

### Terminal 2: Run All Tests
```bash
cd /Users/karirakesh/Documents/Apexon/ExchangeService/kafka-test-payloads
./quick-test.sh all
```

### Terminal 3: Watch Logs (Optional)
```bash
cd /Users/karirakesh/Documents/Apexon/ExchangeService
tail -f logs/application.log
```

---

## Run Specific Tests

```bash
./quick-test.sh limit       # Limit orders (4 orders)
./quick-test.sh market      # Market orders (4 orders)
./quick-test.sh stop        # Stop loss (7 orders)
./quick-test.sh stoplimit   # Stop limit (6 orders)
./quick-test.sh trailing    # Trailing stops (7 orders)
./quick-test.sh iceberg     # Iceberg orders (4 orders)
./quick-test.sh oco         # OCO orders (7 orders)
./quick-test.sh tif         # Time in force (7 orders)
```

---

## Monitoring Commands

```bash
# Watch trade executions
tail -f logs/application.log | grep "Trade executed"

# Watch order book state
tail -f logs/application.log | grep -A 20 "ORDER BOOK STATE"

# Check errors
tail -f logs/application.log | grep -E "ERROR|WARN"

# OCO events
tail -f logs/application.log | grep "OCO"

# Stop triggers
tail -f logs/application.log | grep -i "trigger"

# Trailing adjustments
tail -f logs/application.log | grep "Updated trailing"
```

---

## Troubleshooting

### Verify Setup
```bash
./verify-setup.sh
```

### Restart Kafka
```bash
brew services restart kafka
```

### Check Kafka
```bash
kcat -b localhost:9092 -L
```

### Check Application
```bash
lsof -i :8080
```

---

## Test Files

| File | Orders | Tests |
|------|--------|-------|
| 01-limit-orders.json | 4 | Matching, FIFO |
| 02-market-orders.json | 4 | Market execution |
| 03-stop-loss-orders.json | 7 | Stop triggers |
| 04-stop-limit-orders.json | 6 | Stop-to-limit |
| 05-trailing-stop-orders.json | 7 | Dynamic stops |
| 06-iceberg-orders.json | 4 | Hidden quantity |
| 07-oco-orders.json | 7 | OCO cancellation |
| 08-time-in-force-tests.json | 7 | IOC/FOK/GTC |

**Total: 46 test orders**

---

## Success Checklist

- [ ] All tests pass without errors
- [ ] Limit orders match correctly
- [ ] Market orders execute immediately
- [ ] Stop orders trigger at right prices
- [ ] Trailing stops adjust dynamically
- [ ] OCO orders cancel counterparts
- [ ] IOC orders partially fill
- [ ] FOK orders reject when needed
- [ ] No ERROR logs

---

## Need Help?

- **Full Guide:** `TESTING-READY.md`
- **Setup Guide:** `QUICK-START.md`
- **Documentation:** `README.md`
- **Test Summary:** `TEST-SUMMARY.md`
- **Manual Commands:** `./manual-test-commands.sh`

---

✅ **STATUS: READY TO TEST**
🎯 **46 test orders ready**
🚀 **Start with: mvn spring-boot:run**
