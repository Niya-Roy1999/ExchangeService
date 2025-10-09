package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.TrailingStopOrder;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Manages trailing stop order logic.
 * Single Responsibility Principle: Handles only trailing stop order operations.
 */
@Component
@Slf4j
public class TrailingStopManager {

    /**
     * Initializes a trailing stop order with current market price
     */
    public void initializeTrailingStop(TrailingStopOrder order, BigDecimal lastTradedPrice) {
        if (lastTradedPrice.compareTo(BigDecimal.ZERO) > 0) {
            if (order.getOrderSide() == OrderSide.SELL) {
                order.setHighestPrice(lastTradedPrice);
            } else {
                order.setLowestPrice(lastTradedPrice);
            }

            if (order.getStopPrice() == null) {
                order.updateStopPrice(lastTradedPrice);
                order.setInitialStopPrice(order.getStopPrice());
            } else {
                order.setInitialStopPrice(order.getStopPrice());
            }

            log.info("Initialized trailing stop order {}: stopPrice={}, highestPrice={}, lowestPrice={}",
                    order.getOrderId(), order.getStopPrice(),
                    order.getHighestPrice(), order.getLowestPrice());
        }
    }

    /**
     * Updates all trailing stop orders based on current price
     */
    public void updateTrailingStops(List<BaseOrder> stopOrders, BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        for (BaseOrder order : stopOrders) {
            if (order instanceof TrailingStopOrder) {
                TrailingStopOrder trailingStopOrder = (TrailingStopOrder) order;
                boolean updated = trailingStopOrder.updateStopPrice(currentPrice);
                if (updated) {
                    log.info("Updated trailing stop {}: new stopPrice={}, highestPrice={}, lowestPrice={}",
                            trailingStopOrder.getOrderId(),
                            trailingStopOrder.getStopPrice(),
                            trailingStopOrder.getHighestPrice(),
                            trailingStopOrder.getLowestPrice());
                }
            }
        }
    }

    /**
     * Checks if a trailing stop order should trigger
     */
    public boolean shouldTrigger(TrailingStopOrder order, BigDecimal currentPrice) {
        return order.shouldTrigger(currentPrice);
    }
}
