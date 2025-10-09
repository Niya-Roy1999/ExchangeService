package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.*;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages stop orders including stop loss, stop limit, and trailing stops.
 * Single Responsibility Principle: Handles only stop order operations.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Getter
public class StopOrderManager {

    private final List<BaseOrder> stopOrders = new ArrayList<>();
    private final TradeExecutor tradeExecutor;
    private final TrailingStopManager trailingStopManager;

    /**
     * Adds a stop order to the list
     */
    public void addStopOrder(BaseOrder order) {
        stopOrders.add(order);
        log.debug("Added stop order: {}", order.getOrderId());
    }

    /**
     * Removes a stop order from the list
     */
    public boolean removeStopOrder(BaseOrder order) {
        return stopOrders.remove(order);
    }

    /**
     * Checks all stop orders and returns those that should trigger
     */
    public List<BaseOrder> checkAndTriggerStopOrders(BigDecimal lastTradedPrice) {
        List<BaseOrder> triggeredOrders = new ArrayList<>();
        Iterator<BaseOrder> iterator = stopOrders.iterator();

        while (iterator.hasNext()) {
            BaseOrder stopOrder = iterator.next();
            boolean triggered = false;

            if (stopOrder instanceof TrailingStopOrder) {
                TrailingStopOrder trailingStopOrder = (TrailingStopOrder) stopOrder;
                triggered = trailingStopManager.shouldTrigger(trailingStopOrder, lastTradedPrice);
                if (triggered) {
                    log.info("Trailing stop order triggered: orderId={}, stopPrice={}, lastTradedPrice={}",
                            trailingStopOrder.getOrderId(),
                            trailingStopOrder.getStopPrice(),
                            lastTradedPrice);
                }
            } else {
                BigDecimal stopPrice = OrderPriceExtractor.getStopPrice(stopOrder);
                if (stopPrice != null) {
                    if (stopOrder.getOrderSide() == OrderSide.BUY &&
                            lastTradedPrice.compareTo(stopPrice) >= 0) {
                        triggered = true;
                    } else if (stopOrder.getOrderSide() == OrderSide.SELL &&
                            lastTradedPrice.compareTo(stopPrice) <= 0) {
                        triggered = true;
                    }
                }
            }

            if (triggered) {
                log.info("Stop order triggered: {}", stopOrder);
                iterator.remove();
                triggeredOrders.add(stopOrder);
            }
        }

        return triggeredOrders;
    }

    /**
     * Converts a stop order to its executable form
     */
    public BaseOrder convertStopOrder(BaseOrder stopOrder) {
        if (stopOrder instanceof StopLossOrder) {
            // Convert to MarketOrder
            MarketOrder marketOrder = new MarketOrder();
            tradeExecutor.copyBaseOrderFields(stopOrder, marketOrder);
            return marketOrder;
        } else if (stopOrder instanceof StopLimitOrder) {
            // Convert to LimitOrder
            StopLimitOrder stopLimit = (StopLimitOrder) stopOrder;
            LimitOrder limitOrder = new LimitOrder();
            tradeExecutor.copyBaseOrderFields(stopOrder, limitOrder);
            limitOrder.setPrice(stopLimit.getLimitPrice());
            return limitOrder;
        } else if (stopOrder instanceof TrailingStopOrder) {
            MarketOrder marketOrder = new MarketOrder();
            tradeExecutor.copyBaseOrderFields(stopOrder, marketOrder);
            log.info("Converted trailing stop {} to market order", stopOrder.getOrderId());
            return marketOrder;
        }
        return null;
    }

    /**
     * Gets all stop orders
     */
    public List<BaseOrder> getAllStopOrders() {
        return new ArrayList<>(stopOrders);
    }

    /**
     * Prints the current state of stop orders
     */
    public void printState() {
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
    }
}
