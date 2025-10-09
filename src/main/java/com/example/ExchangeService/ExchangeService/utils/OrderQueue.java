package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Manages buy and sell order queues.
 * Single Responsibility Principle: Handles only order queue operations.
 */
@Component
@Slf4j
@Getter
public class OrderQueue {

    // Buy orders: higher price first, then older timestamp
    private final PriorityQueue<BaseOrder> buyOrders = new PriorityQueue<>(
            (o1, o2) -> {
                BigDecimal price1 = OrderPriceExtractor.extractPrice(o1);
                BigDecimal price2 = OrderPriceExtractor.extractPrice(o2);
                int cmp = price2.compareTo(price1); // higher price first
                return cmp != 0 ? cmp : o1.getTimestamp().compareTo(o2.getTimestamp());
            });

    // Sell orders: lower price first, then older timestamp
    private final PriorityQueue<BaseOrder> sellOrders = new PriorityQueue<>(
            (o1, o2) -> {
                BigDecimal price1 = OrderPriceExtractor.extractPrice(o1);
                BigDecimal price2 = OrderPriceExtractor.extractPrice(o2);
                int cmp = price1.compareTo(price2); // lower price first
                return cmp != 0 ? cmp : o1.getTimestamp().compareTo(o2.getTimestamp());
            });

    private final List<BaseOrder> waitingMarketOrders = new ArrayList<>();

    /**
     * Gets the opposite queue for a given order side
     */
    public PriorityQueue<BaseOrder> getOppositeQueue(OrderSide orderSide) {
        return orderSide == OrderSide.BUY ? sellOrders : buyOrders;
    }

    /**
     * Gets the queue for a given order side
     */
    public PriorityQueue<BaseOrder> getQueue(OrderSide orderSide) {
        return orderSide == OrderSide.BUY ? buyOrders : sellOrders;
    }

    /**
     * Adds an order to the appropriate queue
     */
    public void addOrder(BaseOrder order) {
        if (order.getOrderSide() == OrderSide.BUY) {
            buyOrders.add(order);
        } else {
            sellOrders.add(order);
        }
        log.debug("Added order {} to {} queue", order.getOrderId(), order.getOrderSide());
    }

    /**
     * Adds a market order to waiting list
     */
    public void addWaitingMarketOrder(BaseOrder order) {
        waitingMarketOrders.add(order);
        log.debug("Added market order {} to waiting list", order.getOrderId());
    }

    /**
     * Removes an order from all queues
     */
    public boolean removeOrder(BaseOrder order) {
        boolean removed = buyOrders.remove(order);
        removed |= sellOrders.remove(order);
        removed |= waitingMarketOrders.remove(order);
        return removed;
    }

    /**
     * Calculates available liquidity for an order
     */
    public int calculateAvailableLiquidity(BaseOrder order) {
        PriorityQueue<BaseOrder> opposite = getOppositeQueue(order.getOrderSide());
        int totalLiquidity = 0;

        for (BaseOrder existingOrder : opposite) {
            boolean canMatch = false;

            if (OrderPriceExtractor.isMarketOrder(order)) {
                canMatch = true;
            } else if (!OrderPriceExtractor.hasPrice(existingOrder)) {
                canMatch = true;
            } else {
                BigDecimal orderPrice = OrderPriceExtractor.getOrderPrice(order);
                BigDecimal existingPrice = OrderPriceExtractor.getOrderPrice(existingOrder);

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

    /**
     * Gets all orders from all queues
     */
    public List<BaseOrder> getAllOrders() {
        List<BaseOrder> allOrders = new ArrayList<>();
        allOrders.addAll(buyOrders);
        allOrders.addAll(sellOrders);
        allOrders.addAll(waitingMarketOrders);
        return allOrders;
    }

    /**
     * Clears waiting market orders and returns them
     */
    public List<BaseOrder> clearWaitingMarketOrders() {
        List<BaseOrder> orders = new ArrayList<>(waitingMarketOrders);
        waitingMarketOrders.clear();
        return orders;
    }

    /**
     * Prints the current state of all queues
     */
    public void printState() {
        if (!buyOrders.isEmpty()) {
            log.info("BUY Orders:");
            buyOrders.forEach(o -> log.info("{}", o));
        }
        if (!sellOrders.isEmpty()) {
            log.info("SELL Orders:");
            sellOrders.forEach(o -> log.info("{}", o));
        }
        if (!waitingMarketOrders.isEmpty()) {
            log.info("WAITING MARKET Orders:");
            waitingMarketOrders.forEach(o -> log.info("{}", o));
        }
    }
}
