package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Refactored OrderBook following SOLID principles.
 * Uses composition and delegation to separate concerns.
 *
 * Single Responsibility: Orchestrates order processing
 * Open/Closed: Extensible through strategy pattern
 * Liskov Substitution: Works with BaseOrder and its subtypes
 * Interface Segregation: Delegates to focused components
 * Dependency Inversion: Depends on abstractions (components)
 */
@Component
@Slf4j
@Getter
@RequiredArgsConstructor
public class OrderBook {

    private final OrderQueue orderQueue;
    private final StopOrderManager stopOrderManager;
    private final OCOOrderManager ocoOrderManager;
    private final TrailingStopManager trailingStopManager;
    private final OrderMatcher orderMatcher;
    private final TradeExecutor tradeExecutor;

    @Setter
    private TimeInForceHandler timeInForceHandler;

    private BigDecimal lastTradedPrice = BigDecimal.ZERO;

    /**
     * Adds a regular order to the order book
     */
    public List<TradeResult> addOrder(BaseOrder order) {
        log.info("Adding order: {}", order);
        List<TradeResult> tradeResults = new ArrayList<>();

        // Validate Time In Force
        if (!timeInForceHandler.validateOrderTIF(order)) {
            log.warn("Order {} rejected due to invalid Time In Force", order.getOrderId());
            tradeResults.add(timeInForceHandler.createCancellationResult(order, "Invalid Time In Force"));
            return tradeResults;
        }

        // Initialize trailing stop if applicable
        if (order instanceof TrailingStopOrder) {
            trailingStopManager.initializeTrailingStop((TrailingStopOrder) order, lastTradedPrice);
        }

        // Route stop orders to stop order manager
        if (OrderPriceExtractor.isStopOrder(order)) {
            stopOrderManager.addStopOrder(order);
            return tradeResults;
        }

        // Process the order
        tradeResults.addAll(processOrder(order));

        // Process waiting market orders
        tradeResults.addAll(processWaitingMarketOrders());

        // Clean up expired orders
        cleanUpExpiredOrders();

        // Print current state
        printOrderBookState();

        return tradeResults;
    }

    /**
     * Adds an OCO order to the order book
     */
    public List<TradeResult> addOCOOrder(OCOOrder ocoOrder) {
        log.info("Adding OCO order group: {}", ocoOrder.getOcoGroupId());
        List<TradeResult> tradeResults = new ArrayList<>();

        // Validate both orders
        if (!timeInForceHandler.validateOrderTIF(ocoOrder.getPrimaryOrder()) ||
                !timeInForceHandler.validateOrderTIF(ocoOrder.getSecondaryOrder())) {
            log.warn("OCO order {} rejected due to invalid Time In Force", ocoOrder.getOcoGroupId());
            tradeResults.add(timeInForceHandler.createCancellationResult(
                    ocoOrder, "Invalid Time In Force for OCO orders"));
            return tradeResults;
        }

        // Store OCO order
        ocoOrderManager.addOCOOrder(ocoOrder);

        // Initialize trailing stops if needed
        BaseOrder primary = ocoOrder.getPrimaryOrder();
        BaseOrder secondary = ocoOrder.getSecondaryOrder();

        if (primary instanceof TrailingStopOrder) {
            trailingStopManager.initializeTrailingStop((TrailingStopOrder) primary, lastTradedPrice);
        }
        if (secondary instanceof TrailingStopOrder) {
            trailingStopManager.initializeTrailingStop((TrailingStopOrder) secondary, lastTradedPrice);
        }

        // Process primary order
        if (OrderPriceExtractor.isStopOrder(primary)) {
            stopOrderManager.addStopOrder(primary);
            log.info("OCO primary order {} added to stop orders", primary.getOrderId());
        } else {
            List<TradeResult> primaryResults = processOrder(primary);
            if (!primaryResults.isEmpty() && primary.getFilledQuantity() > 0) {
                ocoOrder.setPrimaryTriggered(true);
                tradeResults.addAll(primaryResults);
                tradeResults.addAll(cancelOCOCounterpart(ocoOrder, secondary));
                ocoOrderManager.removeOCOOrder(ocoOrder.getOcoGroupId());
                cleanUpExpiredOrders();
                printOrderBookState();
                return tradeResults;
            }
        }

        // Process secondary order
        if (OrderPriceExtractor.isStopOrder(secondary)) {
            stopOrderManager.addStopOrder(secondary);
            log.info("OCO secondary order {} added to stop orders", secondary.getOrderId());
        } else {
            List<TradeResult> secondaryResults = processOrder(secondary);
            if (!secondaryResults.isEmpty() && secondary.getFilledQuantity() > 0) {
                ocoOrder.setSecondaryTriggered(true);
                tradeResults.addAll(secondaryResults);
                tradeResults.addAll(cancelOCOCounterpart(ocoOrder, primary));
                ocoOrderManager.removeOCOOrder(ocoOrder.getOcoGroupId());
                cleanUpExpiredOrders();
                printOrderBookState();
                return tradeResults;
            }
        }

        log.info("OCO order {} placed successfully with both legs pending", ocoOrder.getOcoGroupId());
        cleanUpExpiredOrders();
        printOrderBookState();
        return tradeResults;
    }

    /**
     * Processes a single order
     */
    private List<TradeResult> processOrder(BaseOrder order) {
        List<TradeResult> tradeResults = new ArrayList<>();

        // Handle Fill-Or-Kill orders
        if (order.getTimeInForce() == com.example.ExchangeService.ExchangeService.enums.TimeInForce.FILL_OR_KILL) {
            int availableLiquidity = orderQueue.calculateAvailableLiquidity(order);
            if (!timeInForceHandler.validateFOK(order, availableLiquidity)) {
                log.info("Fill Or Kill order {} rejected - insufficient liquidity", order.getOrderId());
                tradeResults.add(timeInForceHandler.createCancellationResult(
                        order, "Fill Or Kill - Insufficient Liquidity"));
                return tradeResults;
            }
        }

        // Try to match the order
        List<TradeResult> matchResults = orderMatcher.matchOrder(order, lastTradedPrice);

        // If null, need to wait (Market × Market with no lastTradedPrice)
        if (matchResults == null) {
            log.info("Market × Market: No lastTradedPrice available, adding to waiting list");
            orderQueue.addWaitingMarketOrder(order);
            return tradeResults;
        }

        // Process match results
        for (TradeResult result : matchResults) {
            lastTradedPrice = result.getExecution().getPrice();
            tradeResults.add(result);

            // Update trailing stops
            trailingStopManager.updateTrailingStops(stopOrderManager.getStopOrders(), lastTradedPrice);

            // Handle OCO executions
            BaseOrder incomingOrder = order;
            BaseOrder existingOrder = result.getOrdersInvolved().get(1);

            OCOOrder incomingOCO = ocoOrderManager.findOCOOrderContaining(incomingOrder);
            OCOOrder existingOCO = ocoOrderManager.findOCOOrderContaining(existingOrder);

            if (incomingOCO != null && incomingOrder.getFilledQuantity() > 0) {
                log.info("OCO order detected execution for incoming order: {}", incomingOrder.getOrderId());
                handleOCOExecution(incomingOCO, incomingOrder, tradeResults);
            }

            if (existingOCO != null && existingOrder.getFilledQuantity() > 0) {
                log.info("OCO order detected execution for existing order: {}", existingOrder.getOrderId());
                handleOCOExecution(existingOCO, existingOrder, tradeResults);
            }

            // Check stop orders after each trade
            tradeResults.addAll(checkStopOrders());
        }

        // Handle Time In Force cancellation
        boolean wasPartiallyFilled = order.getFilledQuantity() > 0 && !order.isFullyFilled();
        if (timeInForceHandler.shouldCancelAfterExecution(order, wasPartiallyFilled)) {
            tradeResults.add(timeInForceHandler.createCancellationResult(
                    order, "Time In Force " + order.getTimeInForce() + " - unfilled portion cancelled"));
            return tradeResults;
        }

        // Add remaining quantity to appropriate queue
        if (!order.isFullyFilled()) {
            if (OrderPriceExtractor.hasPrice(order)) {
                orderQueue.addOrder(order);
            } else if (OrderPriceExtractor.isMarketOrder(order)) {
                orderQueue.addWaitingMarketOrder(order);
            }
        }

        return tradeResults;
    }

    /**
     * Processes waiting market orders
     */
    private List<TradeResult> processWaitingMarketOrders() {
        List<TradeResult> tradeResults = new ArrayList<>();
        List<BaseOrder> waitingOrders = orderQueue.clearWaitingMarketOrders();

        if (waitingOrders.isEmpty()) {
            return tradeResults;
        }

        log.info("Processing {} waiting market orders", waitingOrders.size());

        for (BaseOrder marketOrder : waitingOrders) {
            if (!marketOrder.isFullyFilled()) {
                tradeResults.addAll(processOrder(marketOrder));
            }
        }

        return tradeResults;
    }

    /**
     * Checks and triggers stop orders
     */
    private List<TradeResult> checkStopOrders() {
        List<TradeResult> allTriggeredResults = new ArrayList<>();
        Queue<BaseOrder> triggeredQueue = new LinkedList<>();

        // Find triggered stop orders
        List<BaseOrder> triggered = stopOrderManager.checkAndTriggerStopOrders(lastTradedPrice);

        for (BaseOrder stopOrder : triggered) {
            // Check if part of OCO group
            OCOOrder ocoOrder = ocoOrderManager.findOCOOrderContaining(stopOrder);
            if (ocoOrder != null) {
                log.info("OCO stop order triggered for group: {}", ocoOrder.getOcoGroupId());
                ocoOrderManager.markLegAsTriggered(ocoOrder, stopOrder);
            }

            // Convert stop order
            BaseOrder convertedOrder = stopOrderManager.convertStopOrder(stopOrder);
            if (convertedOrder != null) {
                triggeredQueue.add(convertedOrder);

                // Cancel OCO counterpart if applicable
                if (ocoOrder != null) {
                    BaseOrder otherLeg = ocoOrderManager.getCounterpartOrder(ocoOrder, stopOrder);
                    if (otherLeg != null) {
                        log.info("Cancelling OCO counterpart {} after stop trigger", otherLeg.getOrderId());
                        allTriggeredResults.addAll(cancelOCOCounterpart(ocoOrder, otherLeg));
                        ocoOrderManager.removeOCOOrder(ocoOrder.getOcoGroupId());
                    }
                }
            }
        }

        // Process triggered stop orders
        while (!triggeredQueue.isEmpty()) {
            BaseOrder stopOrder = triggeredQueue.poll();
            List<TradeResult> results = processOrder(stopOrder);
            allTriggeredResults.addAll(results);

            // Recursively check for newly triggered stop orders
            List<TradeResult> newlyTriggered = checkStopOrders();
            allTriggeredResults.addAll(newlyTriggered);
        }

        return allTriggeredResults;
    }

    /**
     * Handles OCO execution
     */
    private void handleOCOExecution(OCOOrder ocoOrder, BaseOrder executedOrder, List<TradeResult> tradeResults) {
        ocoOrderManager.markLegAsTriggered(ocoOrder, executedOrder);
        BaseOrder counterpart = ocoOrderManager.getCounterpartOrder(ocoOrder, executedOrder);

        if (counterpart != null) {
            tradeResults.addAll(cancelOCOCounterpart(ocoOrder, counterpart));
        }

        ocoOrderManager.removeOCOOrder(ocoOrder.getOcoGroupId());
    }

    /**
     * Cancels the counterpart of an OCO order
     */
    private List<TradeResult> cancelOCOCounterpart(OCOOrder ocoOrder, BaseOrder orderToCancel) {
        log.info("Cancelling OCO counterpart order: {} from group {}",
                orderToCancel.getOrderId(), ocoOrder.getOcoGroupId());

        // Remove from all queues
        boolean removed = orderQueue.removeOrder(orderToCancel);
        removed |= stopOrderManager.removeStopOrder(orderToCancel);

        if (removed) {
            log.info("Successfully removed OCO counterpart order {} from order book",
                    orderToCancel.getOrderId());
        } else {
            log.warn("OCO counterpart order {} was not found in any queue",
                    orderToCancel.getOrderId());
        }

        ocoOrder.setCancelledOrderId(orderToCancel.getOrderId());

        List<TradeResult> results = new ArrayList<>();
        results.add(timeInForceHandler.createCancellationResult(
                orderToCancel, "OCO - Counterpart order executed"));

        return results;
    }

    /**
     * Cleans up expired orders from all queues
     */
    private void cleanUpExpiredOrders() {
        List<BaseOrder> allOrders = new ArrayList<>();
        allOrders.addAll(orderQueue.getAllOrders());
        allOrders.addAll(stopOrderManager.getAllStopOrders());

        List<BaseOrder> expiredOrders = timeInForceHandler.getExpiredOrders(allOrders);

        for (BaseOrder expired : expiredOrders) {
            log.info("Removing expired order: {}", expired.getOrderId());
            orderQueue.removeOrder(expired);
            stopOrderManager.removeStopOrder(expired);
        }

        // Clean up expired OCO orders
        List<String> expiredOCOGroups = new ArrayList<>();
        for (OCOOrder oco : ocoOrderManager.getAllOCOOrders()) {
            boolean primaryExpired = timeInForceHandler.getExpiredOrders(
                    Collections.singletonList(oco.getPrimaryOrder())).size() > 0;
            boolean secondaryExpired = timeInForceHandler.getExpiredOrders(
                    Collections.singletonList(oco.getSecondaryOrder())).size() > 0;

            if (primaryExpired || secondaryExpired) {
                log.info("Removing expired OCO order group: {}", oco.getOcoGroupId());

                // Remove both legs
                orderQueue.removeOrder(oco.getPrimaryOrder());
                orderQueue.removeOrder(oco.getSecondaryOrder());
                stopOrderManager.removeStopOrder(oco.getPrimaryOrder());
                stopOrderManager.removeStopOrder(oco.getSecondaryOrder());

                expiredOCOGroups.add(oco.getOcoGroupId());
            }
        }

        expiredOCOGroups.forEach(ocoOrderManager::removeOCOOrder);
    }

    /**
     * Prints the current state of the order book
     */
    private void printOrderBookState() {
        log.info("===== ORDER BOOK STATE =====");
        orderQueue.printState();
        stopOrderManager.printState();
        ocoOrderManager.printState();
        log.info("Last Traded Price: {}", lastTradedPrice);
        log.info("============================");
    }
}
