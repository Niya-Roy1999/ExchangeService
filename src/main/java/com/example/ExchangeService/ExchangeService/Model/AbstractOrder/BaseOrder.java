package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseOrder {
    private String orderId;
    private String userId;
    private String instrumentId;
    private OrderSide orderSide;
    private OrderType orderType;
    private int quantity;
    private int filledQuantity = 0;
    private TimeInForce timeInForce;
    private Instant timestamp;

    private Instant goodTillDate;
    private Instant expiryTime;

    protected BaseOrder(OrderType orderType) {
        this.orderType = orderType;
    }

    public boolean isFullyFilled() { return filledQuantity >= quantity; }
    public int getRemainingQuantity() { return quantity - filledQuantity; }
}
