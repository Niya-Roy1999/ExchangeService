package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Handles order matching logic.
 * Single Responsibility Principle: Handles only order matching and execution logic.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderMatcher {

    private final TradeExecutor tradeExecutor;
    private final OrderQueue orderQueue;

    /**
     * Result of matching attempt
     */
    public static class MatchResult {
        public boolean canMatch;
        public BigDecimal executionPrice;
        public String reason;

        public MatchResult(boolean canMatch, BigDecimal executionPrice, String reason) {
            this.canMatch = canMatch;
            this.executionPrice = executionPrice;
            this.reason = reason;
        }
    }

    /**
     * Determines if two orders can match and at what price
     */
    public MatchResult determineMatch(BaseOrder incomingOrder, BaseOrder existingOrder, BigDecimal lastTradedPrice) {
        boolean incomingIsMarket = OrderPriceExtractor.isMarketOrder(incomingOrder);
        boolean existingIsMarket = OrderPriceExtractor.isMarketOrder(existingOrder);
        BigDecimal incomingPrice = OrderPriceExtractor.getOrderPrice(incomingOrder);
        BigDecimal existingPrice = OrderPriceExtractor.getOrderPrice(existingOrder);

        // Market × Market case
        if (incomingIsMarket && existingIsMarket) {
            if (lastTradedPrice.compareTo(BigDecimal.ZERO) > 0) {
                log.info("Market × Market execution at lastTradedPrice: {}", lastTradedPrice);
                return new MatchResult(true, lastTradedPrice, "Market × Market");
            } else {
                log.info("Market × Market: No lastTradedPrice available");
                return new MatchResult(false, null, "No lastTradedPrice available");
            }
        }

        // Market × Limit case
        if (incomingIsMarket && existingPrice != null) {
            log.info("Market × Limit execution at limit price: {}", existingPrice);
            return new MatchResult(true, existingPrice, "Market × Limit");
        }

        // Limit × Market case
        if (incomingPrice != null && existingIsMarket) {
            log.info("Limit × Market execution at limit price: {}", incomingPrice);
            return new MatchResult(true, incomingPrice, "Limit × Market");
        }

        // Limit × Limit case
        if (incomingPrice != null && existingPrice != null) {
            boolean canMatch;
            if (incomingOrder.getOrderSide() == com.example.ExchangeService.ExchangeService.enums.OrderSide.BUY) {
                canMatch = incomingPrice.compareTo(existingPrice) >= 0;
            } else {
                canMatch = incomingPrice.compareTo(existingPrice) <= 0;
            }

            if (canMatch) {
                // Price improvement - use existing order's price
                log.info("Limit × Limit execution at best price: {}", existingPrice);
                return new MatchResult(true, existingPrice, "Limit × Limit");
            }
        }

        return new MatchResult(false, null, "No match");
    }

    /**
     * Executes a match between incoming and existing order
     */
    public Execution executeMatch(BaseOrder incomingOrder, BaseOrder existingOrder,
                                   BigDecimal executionPrice, BigDecimal lastTradedPrice) {
        int tradableQuantity = Math.min(existingOrder.getRemainingQuantity(),
                incomingOrder.getRemainingQuantity());

        Execution execution = tradeExecutor.executeTrade(incomingOrder, existingOrder,
                tradableQuantity, executionPrice);

        return execution;
    }

    /**
     * Attempts to match an order with opposite side orders
     */
    public List<TradeResult> matchOrder(BaseOrder order, BigDecimal lastTradedPrice) {
        List<TradeResult> tradeResults = new ArrayList<>();
        PriorityQueue<BaseOrder> opposite = orderQueue.getOppositeQueue(order.getOrderSide());

        while (!opposite.isEmpty() && !order.isFullyFilled()) {
            BaseOrder bestOrder = opposite.peek();

            MatchResult matchResult = determineMatch(order, bestOrder, lastTradedPrice);

            if (!matchResult.canMatch) {
                // If Market × Market with no lastTradedPrice, indicate need to wait
                if (matchResult.reason.equals("No lastTradedPrice available")) {
                    return null; // Signal to add to waiting list
                }
                break;
            }

            // Execute the trade
            Execution execution = executeMatch(order, bestOrder, matchResult.executionPrice, lastTradedPrice);
            tradeResults.add(new TradeResult(execution, List.of(order, bestOrder)));

            // Remove fully filled orders
            if (bestOrder.isFullyFilled()) {
                opposite.poll();
            }
        }

        return tradeResults;
    }
}
