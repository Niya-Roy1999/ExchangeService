package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Repositories.OrderStatusRepository;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.entities.Order;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Slf4j
@Getter
public class OrderBook {

    @Setter
    private TimeInForceHandler timeInForceHandler;

    // Buy orders: higher price first, then older timestamp
    private PriorityQueue<Order> buyOrders = new PriorityQueue<>(
            (o1, o2) -> {
                BigDecimal price1 = o1.getPrice() != null ? o1.getPrice() : BigDecimal.ZERO;
                BigDecimal price2 = o2.getPrice() != null ? o2.getPrice() : BigDecimal.ZERO;
                int cmp = price2.compareTo(price1); // higher price first
                return cmp != 0 ? cmp : o1.getTimeStamp().compareTo(o2.getTimeStamp());
            });

    // Sell orders: lower price first, then older timestamp
    private PriorityQueue<Order> sellOrders = new PriorityQueue<>(
            (o1, o2) -> {
                BigDecimal price1 = o1.getPrice() != null ? o1.getPrice() : BigDecimal.ZERO;
                BigDecimal price2 = o2.getPrice() != null ? o2.getPrice() : BigDecimal.ZERO;
                int cmp = price1.compareTo(price2); // lower price first
                return cmp != 0 ? cmp : o1.getTimeStamp().compareTo(o2.getTimeStamp());
            });

    private List<Order> stopOrders = new ArrayList<>();
    private List<Order> waitingMarketOrders = new ArrayList<>(); // market orders waiting for price
    private BigDecimal lastTradedPrice = BigDecimal.ZERO;

    public List<TradeResult> addOrder(Order order) {
        log.info("Adding order: {}", order);
        List<TradeResult> tradeResults = new ArrayList<>();

        if(!timeInForceHandler.validateOrderTIF(order)) {
            log.warn("Order {} rejected due to invalid Time In Force", order.getOrderId());
            tradeResults.add(timeInForceHandler.createCancellationResult(order, "Invalid Time In Force"));
            return tradeResults;
        }

        // Stop orders go to stopOrders list
        if (order.getOrderType() == OrderType.STOP_MARKET || order.getOrderType() == OrderType.STOP_LIMIT) {
            stopOrders.add(order);
            return tradeResults;
        }
        // Process the order
        tradeResults.addAll(processOrder(order));
        // Process any waiting market orders after a trade happens
        tradeResults.addAll(processWaitingMarketOrders());
        // Cleaning up Expired Orders
        cleanUpExpiredOrders();
        printOrderBookState();
        return tradeResults;
    }

    private int calculateAvailableLiquidity(Order order) {
        PriorityQueue<Order> opposite = order.getOrderSide() == OrderSide.BUY ? sellOrders : buyOrders;
        int totalLiquidity = 0;

        for(Order existingOrder: opposite) {
            boolean canMatch = false;
            if(order.getOrderType() == OrderType.MARKET) canMatch = true;
            else if(existingOrder.getPrice() == null) canMatch = true;
            else {
                if(order.getOrderSide() == OrderSide.BUY) {
                    canMatch = order.getPrice().compareTo(existingOrder.getPrice()) >= 0;
                } else {
                    canMatch = order.getPrice().compareTo(existingOrder.getPrice()) <= 0;
                }
            }
            if(canMatch) totalLiquidity += (existingOrder.getQuantity() - existingOrder.getFilledQuantity());
            else break;
        } return totalLiquidity;
    }

    private void cleanUpExpiredOrders() {
        List<Order> allOrders = new ArrayList<>();
        allOrders.addAll(buyOrders);
        allOrders.addAll(sellOrders);
        allOrders.addAll(stopOrders);
        allOrders.addAll(waitingMarketOrders);

        List<Order> expiredOrders = timeInForceHandler.getExpiredOrders(allOrders);
        for(Order expired: expiredOrders) {
            log.info("Removing expired orders: {}", expired.getOrderId());
            buyOrders.remove(expired);
            sellOrders.remove(expired);
            stopOrders.remove(expired);
            waitingMarketOrders.remove(expired);
        }
    }

    private List<TradeResult> processOrder(Order order) {
        List<TradeResult> tradeResults = new ArrayList<>();

        // Handling for FOK orders - checking if they can be completely filled or not
        if(order.getTimeInForce() == TimeInForce.FILL_OR_KILL) {
            int availableLiquidity = calculateAvailableLiquidity(order);
            if(!timeInForceHandler.validateFOK(order, availableLiquidity)) {
                log.info("Fill Or Kill order {} rejected - insufficient liquidity", order.getOrderId());
                tradeResults.add(timeInForceHandler.createCancellationResult(order, "Fill Or Kill - Insufficient Liquidity"));
                return tradeResults;
            }
        }

        // Check opposite heap
        PriorityQueue<Order> opposite = order.getOrderSide() == OrderSide.BUY ? sellOrders : buyOrders;
        while (!opposite.isEmpty() && order.getQuantity() > order.getFilledQuantity()) {
            Order bestOrder = opposite.peek();
            BigDecimal executionPrice;
            boolean canMatch = false;
            if (order.getOrderType() == OrderType.MARKET && bestOrder.getPrice() == null) {
                // Market × Market case
                if (lastTradedPrice.compareTo(BigDecimal.ZERO) > 0) {
                    executionPrice = lastTradedPrice;
                    canMatch = true;
                    log.info("Market × Market execution at lastTradedPrice: {}", executionPrice);
                } else {
                    // No lastTradedPrice available - wait
                    log.info("Market × Market: No lastTradedPrice available, adding to waiting list");
                    waitingMarketOrders.add(order);
                    return tradeResults;
                }
            } else if (order.getOrderType() == OrderType.MARKET && bestOrder.getPrice() != null) {
                // Market × Limit case
                executionPrice = bestOrder.getPrice();
                canMatch = true;
                log.info("Market × Limit execution at limit price: {}", executionPrice);
            } else if (order.getOrderType() == OrderType.LIMIT && bestOrder.getPrice() == null) {
                // Limit × Market case
                executionPrice = order.getPrice();
                canMatch = true;
                log.info("Limit × Market execution at limit price: {}", executionPrice);
            } else {
                // Limit × Limit case
                executionPrice = order.getPrice();
                if (order.getOrderSide() == OrderSide.BUY) {
                    canMatch = executionPrice.compareTo(bestOrder.getPrice()) >= 0;
                } else {
                    canMatch = executionPrice.compareTo(bestOrder.getPrice()) <= 0;
                }
                if (canMatch) {
                    executionPrice = bestOrder.getPrice(); // Price improvement
                    log.info("Limit × Limit execution at best price: {}", executionPrice);
                }
            }
            if (!canMatch)  break;
            // Execute the trade
            int tradableQuantity = Math.min(bestOrder.getQuantity() - bestOrder.getFilledQuantity(),
                    order.getQuantity() - order.getFilledQuantity());

            Execution execution = executeTrade(order, bestOrder, tradableQuantity, executionPrice);
            tradeResults.add(new TradeResult(execution, List.of(order, bestOrder)));

            // Removing fully filled orders
            if (bestOrder.getFilledQuantity() == bestOrder.getQuantity()) {
                opposite.poll();
            }

            // Check stop orders after each trade
            tradeResults.addAll(checkStopOrders());
        }

        boolean wasPartiallyFilled = order.getFilledQuantity() > 0 && order.getFilledQuantity() < order.getQuantity();
        if(timeInForceHandler.shouldCancelAfterExecution(order, wasPartiallyFilled)) {
            tradeResults.add(timeInForceHandler.createCancellationResult(order, "Time In Force " + order.getTimeInForce() + " - unfilled portion cancelled"));
            return tradeResults;
        }

        // Add remaining quantity to appropriate book/waiting list
        if (order.getQuantity() > order.getFilledQuantity()) {
            if (order.getOrderType() == OrderType.LIMIT) {
                if (order.getOrderSide() == OrderSide.BUY) {
                    buyOrders.add(order);
                } else {
                    sellOrders.add(order);
                }
            } else if (order.getOrderType() == OrderType.MARKET) {
                waitingMarketOrders.add(order);
            }
        }
        return tradeResults;
    }

    private List<TradeResult> processWaitingMarketOrders() {
        List<TradeResult> tradeResults = new ArrayList<>();
        if (waitingMarketOrders.isEmpty()) {
            return tradeResults;
        }
        log.info("Processing {} waiting market orders", waitingMarketOrders.size());
        // Process waiting market orders (avoid recursion)
        List<Order> ordersToProcess = new ArrayList<>(waitingMarketOrders);
        waitingMarketOrders.clear();
        for (Order marketOrder : ordersToProcess) {
            if (marketOrder.getQuantity() > marketOrder.getFilledQuantity()) {
                tradeResults.addAll(processOrder(marketOrder));
            }
        }
        return tradeResults;
    }

    private void printOrderBookState() {
        log.info("===== ORDER BOOK STATE =====");
        if (!buyOrders.isEmpty()) {
            log.info("BUY Orders:");
            buyOrders.forEach(o -> log.info("{}", o));
        }
        if (!sellOrders.isEmpty()) {
            log.info("SELL Orders:");
            sellOrders.forEach(o -> log.info("{}", o));
        }
        if (!stopOrders.isEmpty()) {
            log.info("STOP Orders:");
            stopOrders.forEach(o -> log.info("{}", o));
        }
        if (!waitingMarketOrders.isEmpty()) {
            log.info("WAITING MARKET Orders:");
            waitingMarketOrders.forEach(o -> log.info("{}", o));
        }
        log.info("Last Traded Price: {}", lastTradedPrice);
        log.info("============================");
    }

    private List<TradeResult> checkStopOrders() {
        List<TradeResult> allTriggeredResults = new ArrayList<>();
        Queue<Order> triggeredQueue = new LinkedList<>();

        // Find all stop orders that should trigger
        Iterator<Order> iterator = stopOrders.iterator();
        while (iterator.hasNext()) {
            Order stopOrder = iterator.next();
            boolean triggered = false;

            if (stopOrder.getOrderSide() == OrderSide.BUY &&
                    stopOrder.getStopPrice() != null &&
                    lastTradedPrice.compareTo(stopOrder.getStopPrice()) >= 0) {
                triggered = true;
            } else if (stopOrder.getOrderSide() == OrderSide.SELL &&
                    stopOrder.getStopPrice() != null &&
                    lastTradedPrice.compareTo(stopOrder.getStopPrice()) <= 0) {
                triggered = true;
            }

            if (triggered) {
                log.info("Stop order triggered: {}", stopOrder);
                iterator.remove();
                // Convert to MARKET or LIMIT
                if (stopOrder.getOrderType() == OrderType.STOP_MARKET) {
                    stopOrder.setOrderType(OrderType.MARKET);
                    stopOrder.setPrice(null); // Ensure market orders have null price
                } else {
                    stopOrder.setOrderType(OrderType.LIMIT);
                }
                triggeredQueue.add(stopOrder);
            }
        }

        // Process triggered stop orders iteratively to avoid recursion
        while (!triggeredQueue.isEmpty()) {
            Order stopOrder = triggeredQueue.poll();
            List<TradeResult> results = processOrder(stopOrder);
            allTriggeredResults.addAll(results);

            // After processing a stop order, new stop orders may now trigger
            List<TradeResult> newlyTriggered = checkStopOrders();
            allTriggeredResults.addAll(newlyTriggered);
        }

        return allTriggeredResults;
    }


    private Execution executeTrade(Order incoming, Order existing, int quantity, BigDecimal executionPrice) {
        log.info("Executing trade: {} units between {} and {}", quantity, incoming.getOrderId(), existing.getOrderId());

        incoming.setFilledQuantity(incoming.getFilledQuantity() + quantity);
        existing.setFilledQuantity(existing.getFilledQuantity() + quantity);
        lastTradedPrice = executionPrice;

        Execution execution = Execution.builder()
                .orderId(Long.parseLong(incoming.getOrderId()))
                .counterOrderId(Long.parseLong(existing.getOrderId()))
                .userId(incoming.getUserId())
                .instrumentSymbol(incoming.getInstrumentId())
                .side(incoming.getOrderSide().name())
                .quantity(BigDecimal.valueOf(quantity))
                .price(executionPrice)
                .notional(executionPrice.multiply(BigDecimal.valueOf(quantity)))
                .executedAt(LocalDateTime.now())
                .build();

        log.info("Trade executed: {} units between Order {} ({} - {}) and Order {} ({} - {}) at price {}",
                quantity,
                incoming.getOrderId(), incoming.getOrderSide(), incoming.getUserId(),
                existing.getOrderId(), existing.getOrderSide(), existing.getUserId(),
                executionPrice
        );
        return execution;
    }
}
