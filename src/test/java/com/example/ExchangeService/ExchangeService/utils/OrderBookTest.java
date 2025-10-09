package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.*;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderBookTest {

    private OrderBook orderBook;
    private TimeInForceHandler timeInForceHandler;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Create components
        OrderQueue orderQueue = new OrderQueue();
        TradeExecutor tradeExecutor = new TradeExecutor();
        TrailingStopManager trailingStopManager = new TrailingStopManager();
        StopOrderManager stopOrderManager = new StopOrderManager(tradeExecutor, trailingStopManager);
        OCOOrderManager ocoOrderManager = new OCOOrderManager();
        OrderMatcher orderMatcher = new OrderMatcher(tradeExecutor, orderQueue);
        timeInForceHandler = new TimeInForceHandler();

        // Create OrderBook with all dependencies
        orderBook = new OrderBook(
            orderQueue,
            stopOrderManager,
            ocoOrderManager,
            trailingStopManager,
            orderMatcher,
            tradeExecutor
        );
        orderBook.setTimeInForceHandler(timeInForceHandler);
        now = LocalDateTime.now();
    }

    private BaseOrder createOrder(String orderId, OrderType type, OrderSide side, int quantity, BigDecimal price, String userId) {
        BaseOrder order;
        if (type == OrderType.MARKET) {
            MarketOrder marketOrder = new MarketOrder();
            order = marketOrder;
        } else {
            LimitOrder limitOrder = new LimitOrder();
            limitOrder.setPrice(price);
            order = limitOrder;
        }

        order.setOrderId(orderId);
        order.setOrderSide(side);
        order.setQuantity(quantity);
        order.setUserId(userId);
        order.setInstrumentId("AAPL");
        order.setTimestamp(now);
        order.setOrderType(type);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        return order;
    }

    private BaseOrder createStopOrder(String orderId, OrderType type, OrderSide side, int quantity, BigDecimal price, BigDecimal stopPrice, String userId) {
        BaseOrder order;
        if (type == OrderType.STOP_MARKET) {
            StopLossOrder stopLossOrder = new StopLossOrder();
            stopLossOrder.setStopPrice(stopPrice);
            order = stopLossOrder;
        } else {
            StopLimitOrder stopLimitOrder = new StopLimitOrder();
            stopLimitOrder.setStopPrice(stopPrice);
            stopLimitOrder.setLimitPrice(price);
            order = stopLimitOrder;
        }

        order.setOrderId(orderId);
        order.setOrderSide(side);
        order.setQuantity(quantity);
        order.setUserId(userId);
        order.setInstrumentId("AAPL");
        order.setTimestamp(now);
        order.setOrderType(type);
        order.setTimeInForce(TimeInForce.GOOD_TILL_CANCEL);
        return order;
    }

    @Nested
    @DisplayName("Limit Order Tests")
    class LimitOrderTests {

        @Test
        @DisplayName("Limit Buy vs Limit Sell - Exact Match")
        void testLimitOrderExactMatch() {
            BaseOrder buyOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("10.00"), "user1");
            BaseOrder sellOrder = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user2");
            orderBook.addOrder(buyOrder);
            List<TradeResult> results = orderBook.addOrder(sellOrder);

            assertEquals(1, results.size());
            Execution execution = results.get(0).getExecution();
            assertEquals(new BigDecimal("100"), execution.getQuantity());
            assertEquals(new BigDecimal("10.00"), execution.getPrice());
            assertEquals(100, buyOrder.getFilledQuantity());
            assertEquals(100, sellOrder.getFilledQuantity());
        }

        @Test
        @DisplayName("Limit Buy vs Limit Sell - Partial Fill")
        void testLimitOrderPartialFill() {
            Order buyOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 150, new BigDecimal("10.00"), "user1");
            Order sellOrder = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user2");
            orderBook.addOrder(buyOrder);
            List<TradeResult> results = orderBook.addOrder(sellOrder);

            assertEquals(1, results.size());
            Execution execution = results.get(0).getExecution();
            assertEquals(new BigDecimal("100"), execution.getQuantity());
            assertEquals(new BigDecimal("10.00"), execution.getPrice());
            assertEquals(100, buyOrder.getFilledQuantity());
            assertEquals(100, sellOrder.getFilledQuantity());
        }

        @Test
        @DisplayName("Limit Buy vs Limit Sell - Price Improvement")
        void testLimitOrderPriceImprovement() {
            Order buyOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("10.50"), "user1");
            Order sellOrder = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user2");

            orderBook.addOrder(sellOrder);
            List<TradeResult> results = orderBook.addOrder(buyOrder);
            assertEquals(1, results.size());
            Execution execution = results.get(0).getExecution();
            assertEquals(new BigDecimal("10.00"), execution.getPrice());
        }

        @Test
        @DisplayName("Limit Orders = No Match Due to Price")
        void testLimitOrderNoMatch() {
            Order buyOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("9.00"), "user1");
            Order sellOrder = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user2");

            orderBook.addOrder(buyOrder);
            List<TradeResult> results = orderBook.addOrder(sellOrder);
            assertEquals(0, results.size());
            assertEquals(0, buyOrder.getFilledQuantity());
            assertEquals(0, sellOrder.getFilledQuantity());
        }

        @Test
        @DisplayName("Multiple Limit Orders - FIFO Execution")
        void testMultipleOrdersFIFO() {
            Order buyOrder1 = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user1");
            Order buyOrder2 = createOrder("2", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user2");
            Order sellOrder = createOrder("3", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user3");

            orderBook.addOrder(buyOrder1);
            orderBook.addOrder(buyOrder2);
            List<TradeResult> results = orderBook.addOrder(sellOrder);
            assertEquals(2, results.size());
            assertEquals("1", String.valueOf(results.get(0).getExecution().getCounterOrderId()));
            assertEquals("2", String.valueOf(results.get(1).getExecution().getCounterOrderId()));
        }
    }

    @Nested
    @DisplayName("Market Order Tests")
    class MarketOrderTests {

        @Test
        @DisplayName("Market Buy vs Limit Sell")
        void testMarketBuyVsLimitSell() {
            Order sellOrder = createOrder("1", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user1");
            Order marketBuy = createOrder("2", OrderType.MARKET, OrderSide.BUY, 100, null, "user2");

            orderBook.addOrder(sellOrder);
            List<TradeResult> results = orderBook.addOrder(marketBuy);
            assertEquals(1, results.size());
            Execution execution = results.get(0).getExecution();
            assertEquals(new BigDecimal("10.00"), execution.getPrice());
            assertEquals(new BigDecimal("100"), execution.getQuantity());
        }

        @Test
        @DisplayName("Market Sell vs Limit Buy")
        void testMarketSellVsLimitBuy() {
            Order buyOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("10.00"), "user1");
            Order marketSell = createOrder("2", OrderType.MARKET, OrderSide.SELL, 100, null, "user2");

            orderBook.addOrder(buyOrder);
            List<TradeResult> results = orderBook.addOrder(marketSell);
            assertEquals(1, results.size());
            Execution execution = results.get(0).getExecution();
            assertEquals(new BigDecimal("10.00"), execution.getPrice());
            assertEquals(new BigDecimal("100"), execution.getQuantity());
        }

        @Test
        @DisplayName("Market Order - No Opposite Orders")
        void testMarketOrderNoOpposite() {
            Order marketBuy = createOrder("1", OrderType.MARKET, OrderSide.BUY, 100, null, "user1");
            List<TradeResult> results = orderBook.addOrder(marketBuy);
            assertEquals(0, results.size());
            assertEquals(0, marketBuy.getFilledQuantity());
        }
    /*
        @Test
        @DisplayName("Market vs Market with Last Traded Price")
        void testMarketVsMarketWithLastPrice() {
            // Step 1: Add initial limit orders to set lastTradedPrice
            Order limitBuy = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user1");
            Order limitSell = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user2");

            orderBook.addOrder(limitBuy);
            orderBook.addOrder(limitSell); // lastTradedPrice = 10.00

            // Step 2: Add waiting market orders
            Order marketSell = createOrder("3", OrderType.MARKET, OrderSide.SELL, 100, null, "user3");
            orderBook.addOrder(marketSell); // goes to waitingMarketOrders

            // Step 3: Add market buy order that should trigger market vs market execution
            Order marketBuy = createOrder("4", OrderType.MARKET, OrderSide.BUY, 100, null, "user4");
            List<TradeResult> results = orderBook.addOrder(marketBuy);

            // Step 4: Assert that some trade happened
            assertFalse(results.isEmpty(), "No trades executed");

            // Step 5: Sum total executed quantity between the two market orders
            BigDecimal totalQuantity = results.stream()
                    .filter(r -> r.getOrdersInvolved().stream()
                            .anyMatch(o -> o.getOrderId().equals("3") || o.getOrderId().equals("4")))
                    .map(TradeResult::getExecution)
                    .map(Execution::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertEquals(new BigDecimal("100"), totalQuantity, "Total executed quantity should be 100");

            // Step 6: Assert all executions happened at lastTradedPrice = 10.00
            boolean allAtLastPrice = results.stream()
                    .filter(r -> r.getOrdersInvolved().stream()
                            .anyMatch(o -> o.getOrderId().equals("3") || o.getOrderId().equals("4")))
                    .allMatch(r -> r.getExecution().getPrice().equals(new BigDecimal("10.00")));

            assertTrue(allAtLastPrice, "All Market vs Market executions should happen at lastTradedPrice 10.00");
        } */

        @Test
        @DisplayName("Market without Market without Last Traded Price")
        void testMarketVSMarketNoLastPrice() {
            Order marketBuy = createOrder("1", OrderType.MARKET, OrderSide.BUY, 100, null, "user1");
            Order marketSell = createOrder("2", OrderType.MARKET, OrderSide.SELL, 100, null, "user2");

            orderBook.addOrder(marketBuy);
            List<TradeResult> results = orderBook.addOrder(marketSell);
            assertEquals(0, results.size());
            assertEquals(0, marketBuy.getFilledQuantity());
            assertEquals(0, marketSell.getFilledQuantity());
        }
    }

    @Nested
    @DisplayName("Stop Order Tests")
    class StopOrderTests {

        @Test
        @DisplayName("Stop Market Buy - Not Triggered Yet")
        void testStopMarketBuyNotTriggeredYet() {
            Order limitBuy = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user1");
            Order limitSell = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user2");
            Order stopMarketBuy = createStopOrder("3", OrderType.STOP_MARKET, OrderSide.BUY, 100, null, new BigDecimal("10.50"), "user3");
            Order triggerSell = createOrder("4", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.40"), "user4");

            // Process initial trades
            orderBook.addOrder(limitBuy);
            orderBook.addOrder(limitSell); // lastTradedPrice = 10.00
            orderBook.addOrder(stopMarketBuy); // stop order waiting
            orderBook.addOrder(triggerSell);   // price still below stopPrice

            // Stop order should NOT trigger yet
            List<Order> stopOrders = orderBook.getStopOrders();
            assertTrue(stopOrders.stream().anyMatch(o -> o.getOrderId().equals("3")));
        }

        @Test
        @DisplayName("Stop Market Buy - Triggered After Trade")
        void testStopMarketBuyTriggeredAfterTrade() {
            Order limitBuy = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user1");
            Order limitSell = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user2");
            Order stopMarketBuy = createStopOrder("3", OrderType.STOP_MARKET, OrderSide.BUY, 100, null, new BigDecimal("10.50"), "user3");
            Order triggerSell = createOrder("4", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.60"), "user4");
            Order triggerBuy  = createOrder("5", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.60"), "user5");

            orderBook.addOrder(limitBuy);
            orderBook.addOrder(limitSell); // lastTradedPrice = 10.00
            orderBook.addOrder(stopMarketBuy); // stop order waiting

            // Trigger trade that moves lastTradedPrice above stop price
            orderBook.addOrder(triggerSell);
            List<TradeResult> results = orderBook.addOrder(triggerBuy);

            // Verify stop order triggered
            boolean stopTriggered = results.stream()
                    .anyMatch(result -> result.getOrdersInvolved().stream()
                            .anyMatch(order -> order.getOrderId().equals("3")));
            assertTrue(orderBook.getWaitingMarketOrders().stream()
                    .anyMatch(o -> o.getOrderId().equals("3")));
        }

        @Test
        @DisplayName("Stop Market Sell - Triggered")
        void testStopMarketSellTriggered() {
            Order limitBuy = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user1");
            Order limitSell = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user2");
            Order stopMarketSell = createStopOrder("3", OrderType.STOP_MARKET, OrderSide.SELL, 100, null, new BigDecimal("9.50"), "user3");

            orderBook.addOrder(limitBuy);
            orderBook.addOrder(limitSell); // lastTradedPrice = 10.00
            orderBook.addOrder(stopMarketSell); // Stop order waiting
            Order triggerSell = createOrder("4", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("9.40"), "user4");
            Order triggerBuy = createOrder("5", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("9.40"), "user5");
            orderBook.addOrder(triggerSell);
            List<TradeResult> results = orderBook.addOrder(triggerBuy); // Should trigger stop order
            assertTrue(results.size() >= 1);
        }

        @Test
        @DisplayName("Stop Limit Order - Not triggered")
        void testStopLimitNotTriggered() {
            Order stopLimitBuy = createStopOrder("1", OrderType.STOP_MARKET, OrderSide.BUY, 100,
            new BigDecimal("11.00"), new BigDecimal("10.50"), "user1");
            List<TradeResult> results = orderBook.addOrder(stopLimitBuy);
            assertEquals(0, results.size());
            assertEquals(0, stopLimitBuy.getFilledQuantity());
        }
    }

    @Nested
    @DisplayName("Mixed Order Type Tests")
    class MixedOrderTests {

        @Test
        @DisplayName("Complex Scenario - Multiple Order Types")
        void testComplexMixedScenarios() {
            Order limitBuy1 = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("9.80"), "user1");
            Order limitBuy2 = createOrder("2", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("10.00"), "user2");
            Order limitSell1 = createOrder("3", OrderType.LIMIT, OrderSide.SELL, 75, new BigDecimal("10.20"), "user3");
            Order marketBuy = createOrder("4", OrderType.MARKET, OrderSide.BUY, 50, null, "user4");

            orderBook.addOrder(limitBuy1);
            orderBook.addOrder(limitBuy2);
            orderBook.addOrder(limitSell1);
            List<TradeResult> results = orderBook.addOrder(marketBuy);

            assertEquals(1, results.size());
            Execution execution = results.get(0).getExecution();
            assertEquals(new BigDecimal("10.20"), execution.getPrice());
            assertEquals(new BigDecimal("50"), execution.getQuantity());
        }

        @Test
        @DisplayName("Order Priority - Price then Time")
        void testOrderPriority() {
            // Given - Orders with different prices and times
            Order buyOrder1 = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user1");
            Order buyOrder2 = createOrder("2", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.10"), "user2"); // Higher price
            Order sellOrder = createOrder("3", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user3");

            orderBook.addOrder(buyOrder1); // Added first
            orderBook.addOrder(buyOrder2); // Higher price, added second
            List<TradeResult> results = orderBook.addOrder(sellOrder);
            // Then - Higher price buy order should match first
            assertEquals(1, results.size());
            assertEquals("2", String.valueOf(results.get(0).getExecution().getCounterOrderId()));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Zero Quantity Order")
        void testZeroQuantityOrder() {
            Order zeroOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 0, new BigDecimal("10.00"), "user1");
            List<TradeResult> results = orderBook.addOrder(zeroOrder);
            assertEquals(0, results.size());
        }

        @Test
        @DisplayName("Multiple Partial Filled")
        void testMultiplePartialFilled() {
            Order largeBuy = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 300, new BigDecimal("10.00"), "user1");
            Order smallSell1 = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user2");
            Order smallSell2 = createOrder("3", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user3");
            Order smallSell3 = createOrder("4", OrderType.LIMIT, OrderSide.SELL, 75, new BigDecimal("10.00"), "user4");
            orderBook.addOrder(largeBuy);
            List<TradeResult> results1 = orderBook.addOrder(smallSell1);
            List<TradeResult> results2 = orderBook.addOrder(smallSell2);
            List<TradeResult> results3 = orderBook.addOrder(smallSell3);
            assertEquals(1, results1.size());
            assertEquals(1, results2.size());
            assertEquals(1, results3.size());
            assertEquals(225, largeBuy.getFilledQuantity());
        }

        @Test
        @DisplayName("Same User Orders")
        void testSameUserOrder() {
            Order buyOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("10.00"), "user1");
            Order sellOrder = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user1");

            orderBook.addOrder(buyOrder);
            List<TradeResult> results = orderBook.addOrder(sellOrder);

            // It should execute
            assertEquals(1, results.size());
            assertEquals(100, buyOrder.getFilledQuantity());
            assertEquals(100, sellOrder.getFilledQuantity());
        }
    }

    @Nested
    @DisplayName("Waiting Market Orders")
    class WaitingMarketOrders {

        @Test
        @DisplayName("Waiting Market Orders Processed After Limit Trade")
        void testWaitingMarketOrdersProcessed() {
            Order marketBuy = createOrder("1", OrderType.MARKET, OrderSide.BUY, 100, null, "user1");
            Order marketSell = createOrder("2", OrderType.MARKET, OrderSide.SELL, 50, null, "user2");
            orderBook.addOrder(marketBuy);
            orderBook.addOrder(marketSell);
            // Add a larger limit buy to establish lastTradedPrice
            Order limitBuy = createOrder("3", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("10.00"), "user3");
            orderBook.addOrder(limitBuy);
            // Add enough limit sells to cover both the limit buy and market buy
            Order limitSell1 = createOrder("4", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.00"), "user4");
            List<TradeResult> results = orderBook.addOrder(limitSell1);
            // Now we should seeing the below ones:
            // - limitBuy × limitSell (100 @ 10.00)
            // - marketBuy × remaining limitSell (up to 100 @ 10.00)
            // - marketSell × any limitBuy if available
            assertTrue(results.size() >= 2);
        }
    }

    @Nested
    @DisplayName("State Validation Tests")
    class StateValidationTests {

        @Test
        @DisplayName("Order Book State After Trades")
        void testOrderBookStateAfterTrades() {
            Order buyOrder = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 100, new BigDecimal("10.00"), "user1");
            Order sellOrder = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user2");
            orderBook.addOrder(buyOrder);
            orderBook.addOrder(sellOrder);
            assertEquals(50, buyOrder.getFilledQuantity());
            assertEquals(50, sellOrder.getFilledQuantity());
            assertEquals(50, buyOrder.getQuantity() - buyOrder.getFilledQuantity());
        }

        @Test
        @DisplayName("Last Traded Price Updates - With Market Trade")
        void testLastTradedPriceUpdatesWithMarket() {
            Order buyOrder1 = createOrder("1", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.00"), "user1");
            Order sellOrder1 = createOrder("2", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.00"), "user2");
            Order buyOrder2 = createOrder("3", OrderType.LIMIT, OrderSide.BUY, 50, new BigDecimal("10.50"), "user3");
            Order sellOrder2 = createOrder("4", OrderType.LIMIT, OrderSide.SELL, 50, new BigDecimal("10.50"), "user4");

            orderBook.addOrder(buyOrder1);
            orderBook.addOrder(sellOrder1); // lastTradedPrice = 10.00
            orderBook.addOrder(buyOrder2);
            orderBook.addOrder(sellOrder2); // lastTradedPrice = 10.50

            // Adding extra liquidity so market order can match
            Order extraSell = createOrder("7", OrderType.LIMIT, OrderSide.SELL, 100, new BigDecimal("10.50"), "user7");
            orderBook.addOrder(extraSell);

            Order marketBuy = createOrder("6", OrderType.MARKET, OrderSide.BUY, 100, null, "user6");
            List<TradeResult> results = orderBook.addOrder(marketBuy);

            assertTrue(results.stream()
                    .anyMatch(result -> result.getExecution().getPrice().equals(new BigDecimal("10.50"))));
        }
    }
}
