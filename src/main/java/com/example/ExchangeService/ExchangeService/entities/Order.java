package com.example.ExchangeService.ExchangeService.entities;

import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Order {

    private String orderId;
    private String userId;
    private String instrumentId;
    private OrderSide orderSide;
    private OrderType orderType;
    private TimeInForce timeInForce;
    private int quantity;
    private int filledQuantity = 0;
    private BigDecimal price;
    private BigDecimal stopPrice;
    private BigDecimal trailingOffset;
    private String trailingType;
    private int displayQuantity;
    private Instant expiryTime;
    private Instant timeStamp;
    private Instant goodTillDate;

    public boolean isFullyFilled() { return filledQuantity >= quantity; }
    public boolean isPartiallyFilled() {return filledQuantity > 0 && filledQuantity < quantity;}
    public int getRemainingQuantity() {return quantity - filledQuantity;}

    @Override
    public String toString() {
        return String.format("Order[id=%s, side=%s, type=%s, price=%s, qty=%d, filled=%d, tif=%s%s]",
                orderId, orderSide, orderType, price, quantity, filledQuantity,
                timeInForce != null ? timeInForce : "GTC",
                timeInForce == TimeInForce.GOOD_TILL_DATE && goodTillDate != null ?
                        ", gtd=" + goodTillDate : "");
    }
}
