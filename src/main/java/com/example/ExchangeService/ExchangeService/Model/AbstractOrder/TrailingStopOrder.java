package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TrailingStopOrder extends BaseOrder{

    private BigDecimal stopPrice;           // Current stop price (dynamic)
    private BigDecimal trailAmount;         // Fixed trail amount (e.g., $5)
    private BigDecimal trailPercent;        // Or percentage trail (e.g., 5%)
    private BigDecimal highestPrice;        // For SELL orders - tracks highest price seen
    private BigDecimal lowestPrice;         // For BUY orders - tracks lowest price seen
    private BigDecimal initialStopPrice;    // Original stop price when order was placed

    public TrailingStopOrder() {
        super(OrderType.TRAILING_STOP);
    }

    public boolean updateStopPrice(BigDecimal currentPrice) {
        if (currentPrice == null) return false;
        if (getOrderSide() == OrderSide.SELL) {
            if (highestPrice == null || currentPrice.compareTo(highestPrice) > 0) {
                highestPrice = currentPrice;
                BigDecimal newStopPrice = calculateNewStopPrice(highestPrice);
                if (stopPrice == null || newStopPrice.compareTo(stopPrice) > 0) {
                    stopPrice = newStopPrice;
                    return true;
                }
            }
        } else if (getOrderSide() == OrderSide.BUY) {
            if (lowestPrice == null || currentPrice.compareTo(lowestPrice) < 0) {
                lowestPrice = currentPrice;
                BigDecimal newStopPrice = calculateNewStopPrice(lowestPrice);
                if (stopPrice == null || newStopPrice.compareTo(stopPrice) < 0) {
                    stopPrice = newStopPrice;
                    return true;
                }
            }
        }
        return false;
    }
    
    public boolean shouldTrigger(BigDecimal currentPrice) {
        if (stopPrice == null || currentPrice == null) return false;
        if (getOrderSide() == OrderSide.SELL) {
            return currentPrice.compareTo(stopPrice) <= 0;
        } else {
            return currentPrice.compareTo(stopPrice) >= 0;
        }
    }

    private BigDecimal calculateNewStopPrice(BigDecimal referencePrice) {
        if (trailAmount != null) {
            // Fixed amount trailing
            if (getOrderSide() == OrderSide.SELL) {
                return referencePrice.subtract(trailAmount);
            } else {
                return referencePrice.add(trailAmount);
            }
        } else if (trailPercent != null) {
            // Percentage trailing
            BigDecimal trailValue = referencePrice.multiply(trailPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (getOrderSide() == OrderSide.SELL) {
                return referencePrice.subtract(trailValue);
            } else {
                return referencePrice.add(trailValue);
            }
        }
        return stopPrice;
    }
}
