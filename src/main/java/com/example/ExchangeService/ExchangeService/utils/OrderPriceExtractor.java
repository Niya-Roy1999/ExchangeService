package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.*;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;

/**
 * Utility class for extracting price information from different order types.
 * Single Responsibility Principle: Handles only price extraction logic.
 */
@UtilityClass
public class OrderPriceExtractor {

    /**
     * Extracts price from order, returns ZERO if no price available
     */
    public BigDecimal extractPrice(BaseOrder order) {
        if (order instanceof LimitOrder) {
            BigDecimal price = ((LimitOrder) order).getPrice();
            return price != null ? price : BigDecimal.ZERO;
        } else if (order instanceof StopLimitOrder) {
            BigDecimal price = ((StopLimitOrder) order).getLimitPrice();
            return price != null ? price : BigDecimal.ZERO;
        } else if (order instanceof IcebergOrder) {
            BigDecimal price = ((IcebergOrder) order).getPrice();
            return price != null ? price : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    /**
     * Checks if order has a price component
     */
    public boolean hasPrice(BaseOrder order) {
        return order instanceof LimitOrder ||
                order instanceof StopLimitOrder ||
                order instanceof IcebergOrder;
    }

    /**
     * Gets order price, returns null for market orders
     */
    public BigDecimal getOrderPrice(BaseOrder order) {
        if (order instanceof LimitOrder) {
            return ((LimitOrder) order).getPrice();
        } else if (order instanceof StopLimitOrder) {
            return ((StopLimitOrder) order).getLimitPrice();
        } else if (order instanceof IcebergOrder) {
            return ((IcebergOrder) order).getPrice();
        }
        return null;
    }

    /**
     * Checks if order is a stop order type
     */
    public boolean isStopOrder(BaseOrder order) {
        return order instanceof StopLossOrder ||
                order instanceof StopLimitOrder ||
                order instanceof TrailingStopOrder;
    }

    /**
     * Checks if order is a market order
     */
    public boolean isMarketOrder(BaseOrder order) {
        return order instanceof MarketOrder;
    }

    /**
     * Gets stop price from stop order types
     */
    public BigDecimal getStopPrice(BaseOrder order) {
        if (order instanceof StopLossOrder) {
            return ((StopLossOrder) order).getStopPrice();
        } else if (order instanceof StopLimitOrder) {
            return ((StopLimitOrder) order).getStopPrice();
        } else if (order instanceof TrailingStopOrder) {
            return ((TrailingStopOrder) order).getStopPrice();
        }
        return null;
    }
}
