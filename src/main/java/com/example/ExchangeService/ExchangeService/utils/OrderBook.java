package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.*;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
    private PriorityQueue<BaseOrder> buyOrders = new PriorityQueue<>(
            (o1, o2) -> {
                BigDecimal price1 = extractPrice(o1);
                BigDecimal price2 = extractPrice(o2);
                int cmp = price2.compareTo(price1); // higher price first
                return cmp != 0 ? cmp : o1.getTimestamp().compareTo(o2.getTimestamp());
            });

    // Sell orders: lower price first, then older timestamp
    private PriorityQueue<BaseOrder> sellOrders = new PriorityQueue<>(
            (o1, o2) -> {
                BigDecimal price1 = extractPrice(o1);
                BigDecimal price2 = extractPrice(o2);
                int cmp = price1.compareTo(price2); // lower price first
                return cmp != 0 ? cmp : o1.getTimestamp().compareTo(o2.getTimestamp());
            });

    private List<BaseOrder> stopOrders = new ArrayList<>();
    private List<BaseOrder> waitingMarketOrders = new ArrayList<>();
    private BigDecimal lastTradedPrice = BigDecimal.ZERO;

    // Helper method to extract price from different order types
    private BigDecimal extractPrice(BaseOrder order) {
        if (order instanceof LimitOrder) {
            BigDecimal price = ((LimitOrder) order).getPrice();
            return price != null ? price : BigDecimal.ZERO;
        } else if (order instanceof StopLimitOrder) {
            BigDecimal price = ((StopLimitOrder) order).getLimitPrice();
            return price != null ? price : BigDecimal.ZERO;
        }  else if (order instanceof IcebergOrder) {
            BigDecimal price = ((IcebergOrder) order).getPrice();
            return price != null ? price : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO; // Market orders and others
    }

    // Helper method to check if order has price
    private boolean hasPrice(BaseOrder order) {
        return order instanceof LimitOrder ||
                order instanceof StopLimitOrder ||
                order instanceof IcebergOrder;
    }

    // Helper method to get price from order (returns null for market orders)
    private BigDecimal getOrderPrice(BaseOrder order) {
        if (order instanceof LimitOrder) {
            return ((LimitOrder) order).getPrice();
        } else if (order instanceof StopLimitOrder) {
            return ((StopLimitOrder) order).getLimitPrice();
        } else if (order instanceof IcebergOrder) {
            return ((IcebergOrder) order).getPrice();
        }
        return null;
    }

    // Helper method to check if order is a stop order
    private boolean isStopOrder(BaseOrder order) {
        return order instanceof StopLossOrder ||
                order instanceof StopLimitOrder ||
                order instanceof TrailingStopOrder;
    }

    // Helper method to check if order is market order
    private boolean isMarketOrder(BaseOrder order) {
        return order instanceof MarketOrder;
    }

    public List<TradeResult> addOrder(BaseOrder order) {
        log.info("Adding order: {}", order);
        List<TradeResult> tradeResults = new ArrayList<>();

        if (!timeInForceHandler.validateOrderTIF(order)) {
            log.warn("Order {} rejected due to invalid Time In Force", order.getOrderId());
            tradeResults.add(timeInForceHandler.createCancellationResult(order, "Invalid Time In Force"));
            return tradeResults;
        }

        if(order instanceof TrailingStopOrder) {
            TrailingStopOrder trailingStopOrder = (TrailingStopOrder) order;
            initializeTrailingStop(trailingStopOrder);
        }

        // Stop orders go to stopOrders list
        if (isStopOrder(order)) {
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

    private void initializeTrailingStop(TrailingStopOrder order) {
        if(lastTradedPrice.compareTo(BigDecimal.ZERO) < 0) {
            if(order.getOrderSide() == OrderSide.SELL) {
                order.setHighestPrice(lastTradedPrice);
            } else order.setLowestPrice(lastTradedPrice);

            if(order.getStopPrice() == null) {
                order.updateStopPrice(lastTradedPrice);
                order.setInitialStopPrice(order.getStopPrice());
            } else order.setInitialStopPrice(order.getStopPrice());
            log.info("Initialized trailing stop order {}: stopPrice={}, highestPrice={}, lowestPrice={}",
                    order.getOrderId(), order.getStopPrice(),
                    order.getHighestPrice(), order.getLowestPrice());
        }
    }

    private void updateTrailingStops(BigDecimal currentPrice) {
        if(currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) return;
        for(BaseOrder order: stopOrders) {
            if(order instanceof TrailingStopOrder) {
                TrailingStopOrder trailingStopOrder =(TrailingStopOrder)  order;
                boolean updated = trailingStopOrder.updateStopPrice(currentPrice);
                if(updated) {
                    log.info("Updated trailing stop {}: new stopPrice={}, highestPrice={}, lowestPrice={}",
                            trailingStopOrder.getOrderId(),
                            trailingStopOrder.getStopPrice(),
                            trailingStopOrder.getHighestPrice(),
                            trailingStopOrder.getLowestPrice());
                }
            }
        }
    }

    private int calculateAvailableLiquidity(BaseOrder order) {
        PriorityQueue<BaseOrder> opposite = order.getOrderSide() == OrderSide.BUY ? sellOrders : buyOrders;
        int totalLiquidity = 0;

        for (BaseOrder existingOrder : opposite) {
            boolean canMatch = false;

            if (isMarketOrder(order)) {
                canMatch = true;
            } else if (!hasPrice(existingOrder)) {
                canMatch = true;
            } else {
                BigDecimal orderPrice = getOrderPrice(order);
                BigDecimal existingPrice = getOrderPrice(existingOrder);

                if (orderPrice != null && existingPrice != null) {
                    if (order.getOrderSide() == OrderSide.BUY) {
                        canMatch = orderPrice.compareTo(existingPrice) >= 0;
                    } else {
                        canMatch = orderPrice.compareTo(existingPrice) <= 0;
                    }
                }
            }
            if (canMatch) {
                totalLiquidity += existingOrder.getRemainingQuantity();
            } else {
                break;
            }
        }
        return totalLiquidity;
    }

    private void cleanUpExpiredOrders() {
        List<BaseOrder> allOrders = new ArrayList<>();
        allOrders.addAll(buyOrders);
        allOrders.addAll(sellOrders);
        allOrders.addAll(stopOrders);
        allOrders.addAll(waitingMarketOrders);

        List<BaseOrder> expiredOrders = timeInForceHandler.getExpiredOrders(allOrders);
        for (BaseOrder expired : expiredOrders) {
            log.info("Removing expired orders: {}", expired.getOrderId());
            buyOrders.remove(expired);
            sellOrders.remove(expired);
            stopOrders.remove(expired);
            waitingMarketOrders.remove(expired);
        }
    }

    private List<TradeResult> processOrder(BaseOrder order) {
        List<TradeResult> tradeResults = new ArrayList<>();

        // Handling for FOK orders
        if (order.getTimeInForce() == TimeInForce.FILL_OR_KILL) {
            int availableLiquidity = calculateAvailableLiquidity(order);
            if (!timeInForceHandler.validateFOK(order, availableLiquidity)) {
                log.info("Fill Or Kill order {} rejected - insufficient liquidity", order.getOrderId());
                tradeResults.add(timeInForceHandler.createCancellationResult(order, "Fill Or Kill - Insufficient Liquidity"));
                return tradeResults;
            }
        }

        // Check opposite heap
        PriorityQueue<BaseOrder> opposite = order.getOrderSide() == OrderSide.BUY ? sellOrders : buyOrders;

        while (!opposite.isEmpty() && !order.isFullyFilled()) {
            BaseOrder bestOrder = opposite.peek();
            BigDecimal executionPrice = null;
            boolean canMatch = false;

            boolean incomingIsMarket = isMarketOrder(order);
            boolean existingIsMarket = isMarketOrder(bestOrder);
            BigDecimal incomingPrice = getOrderPrice(order);
            BigDecimal existingPrice = getOrderPrice(bestOrder);

            if (incomingIsMarket && existingIsMarket) {
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
            } else if (incomingIsMarket && existingPrice != null) {
                // Market × Limit case
                executionPrice = existingPrice;
                canMatch = true;
                log.info("Market × Limit execution at limit price: {}", executionPrice);
            } else if (incomingPrice != null && existingIsMarket) {
                // Limit × Market case
                executionPrice = incomingPrice;
                canMatch = true;
                log.info("Limit × Market execution at limit price: {}", executionPrice);
            } else if (incomingPrice != null && existingPrice != null) {
                // Limit × Limit case
                executionPrice = incomingPrice;
                if (order.getOrderSide() == OrderSide.BUY) {
                    canMatch = executionPrice.compareTo(existingPrice) >= 0;
                } else {
                    canMatch = executionPrice.compareTo(existingPrice) <= 0;
                }
                if (canMatch) {
                    executionPrice = existingPrice; // Price improvement
                    log.info("Limit × Limit execution at best price: {}", executionPrice);
                }
            }
            if (!canMatch) break;
            // Execute the trade
            int tradableQuantity = Math.min(bestOrder.getRemainingQuantity(), order.getRemainingQuantity());
            Execution execution = executeTrade(order, bestOrder, tradableQuantity, executionPrice);
            tradeResults.add(new TradeResult(execution, List.of(order, bestOrder)));
            // Removing fully filled orders
            if (bestOrder.isFullyFilled()) {
                opposite.poll();
            }
            // Check stop orders after each trade
            tradeResults.addAll(checkStopOrders());
        }

        boolean wasPartiallyFilled = order.getFilledQuantity() > 0 && !order.isFullyFilled();
        if (timeInForceHandler.shouldCancelAfterExecution(order, wasPartiallyFilled)) {
            tradeResults.add(timeInForceHandler.createCancellationResult(order, "Time In Force " + order.getTimeInForce() + " - unfilled portion cancelled"));
            return tradeResults;
        }

        // Add remaining quantity to appropriate book/waiting list
        if (!order.isFullyFilled()) {
            if (hasPrice(order)) {
                if (order.getOrderSide() == OrderSide.BUY) {
                    buyOrders.add(order);
                } else {
                    sellOrders.add(order);
                }
            } else if (isMarketOrder(order)) {
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
        List<BaseOrder> ordersToProcess = new ArrayList<>(waitingMarketOrders);
        waitingMarketOrders.clear();
        for (BaseOrder marketOrder : ordersToProcess) {
            if (!marketOrder.isFullyFilled()) {
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
            stopOrders.forEach(o -> {
                if (o instanceof TrailingStopOrder) {
                    TrailingStopOrder ts = (TrailingStopOrder) o;
                    log.info("TrailingStop: orderId={}, side={}, stopPrice={}, trailAmount={}, highest={}, lowest={}",
                            ts.getOrderId(), ts.getOrderSide(), ts.getStopPrice(),
                            ts.getTrailAmount(), ts.getHighestPrice(), ts.getLowestPrice());
                } else {
                    log.info("{}", o);
                }
            });
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
        Queue<BaseOrder> triggeredQueue = new LinkedList<>();

        // Find all stop orders that should trigger
        Iterator<BaseOrder> iterator = stopOrders.iterator();
        while (iterator.hasNext()) {
            BaseOrder stopOrder = iterator.next();
            boolean triggered = false;
            BigDecimal stopPrice = null;

            if(stopOrder instanceof  TrailingStopOrder) {
                TrailingStopOrder trailingStopOrder = (TrailingStopOrder) stopOrder;
                triggered = trailingStopOrder.shouldTrigger(lastTradedPrice);
                if(triggered) {
                    log.info("Trailing stop order triggered: orderId={}, stopPrice={}, lastTradedPrice={}",
                            trailingStopOrder.getOrderId(),
                            trailingStopOrder.getStopPrice(),
                            lastTradedPrice);
                }
            }
            else if (stopOrder instanceof StopLossOrder) {
                stopPrice = ((StopLossOrder) stopOrder).getStopPrice();
            } else if (stopOrder instanceof StopLimitOrder) {
                stopPrice = ((StopLimitOrder) stopOrder).getStopPrice();
            } else if (stopOrder instanceof TrailingStopOrder) {
                stopPrice = ((TrailingStopOrder) stopOrder).getStopPrice();
            }

            if (stopPrice != null) {
                if (stopOrder.getOrderSide() == OrderSide.BUY &&
                        lastTradedPrice.compareTo(stopPrice) >= 0) {
                    triggered = true;
                } else if (stopOrder.getOrderSide() == OrderSide.SELL &&
                        lastTradedPrice.compareTo(stopPrice) <= 0) {
                    triggered = true;
                }
            }

            if (triggered) {
                log.info("Stop order triggered: {}", stopOrder);
                iterator.remove();

                // Convert to appropriate order type
                BaseOrder convertedOrder = convertStopOrder(stopOrder);
                if (convertedOrder != null) {
                    triggeredQueue.add(convertedOrder);
                }
            }
        }

        // Process triggered stop orders iteratively
        while (!triggeredQueue.isEmpty()) {
            BaseOrder stopOrder = triggeredQueue.poll();
            List<TradeResult> results = processOrder(stopOrder);
            allTriggeredResults.addAll(results);

            // After processing a stop order, new stop orders may now trigger
            List<TradeResult> newlyTriggered = checkStopOrders();
            allTriggeredResults.addAll(newlyTriggered);
        }

        return allTriggeredResults;
    }

    private BaseOrder convertStopOrder(BaseOrder stopOrder) {
        if (stopOrder instanceof StopLossOrder) {
            // Convert to MarketOrder
            MarketOrder marketOrder = new MarketOrder();
            copyBaseOrderFields(stopOrder, marketOrder);
            return marketOrder;
        } else if (stopOrder instanceof StopLimitOrder) {
            // Convert to LimitOrder
            StopLimitOrder stopLimit = (StopLimitOrder) stopOrder;
            LimitOrder limitOrder = new LimitOrder();
            copyBaseOrderFields(stopOrder, limitOrder);
            limitOrder.setPrice(stopLimit.getLimitPrice());
            return limitOrder;
        } else if(stopOrder instanceof  TrailingStopOrder) {
            MarketOrder marketOrder = new MarketOrder();
            copyBaseOrderFields(stopOrder, marketOrder);
            log.info("Converted trailing stop {} to market order", stopOrder.getOrderId());
            return marketOrder;
        }
        return null;
    }

    private void copyBaseOrderFields(BaseOrder source, BaseOrder target) {
        target.setOrderId(source.getOrderId());
        target.setUserId(source.getUserId());
        target.setInstrumentId(source.getInstrumentId());
        target.setOrderSide(source.getOrderSide());
        target.setOrderType(source.getOrderType());
        target.setQuantity(source.getQuantity());
        target.setFilledQuantity(source.getFilledQuantity());
        target.setTimeInForce(source.getTimeInForce());
        target.setTimestamp(source.getTimestamp());
        target.setGoodTillDate(source.getGoodTillDate());
        target.setExpiryTime(source.getExpiryTime());
    }

    private Execution executeTrade(BaseOrder incoming, BaseOrder existing, int quantity, BigDecimal executionPrice) {
        log.info("Executing trade: {} units between {} and {}", quantity, incoming.getOrderId(), existing.getOrderId());

        incoming.setFilledQuantity(incoming.getFilledQuantity() + quantity);
        existing.setFilledQuantity(existing.getFilledQuantity() + quantity);
        lastTradedPrice = executionPrice;
        updateTrailingStops(executionPrice);

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