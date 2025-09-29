package com.example.ExchangeService.ExchangeService.entities;

import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
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
    private int quantity;
    private int filledQuantity = 0;
    private BigDecimal price;
    private BigDecimal stopPrice;
    private BigDecimal trailingOffset;
    private String trailingType;
    private int displayQuantity;
    private Instant expiryTime;
    private Instant timeStamp;
}
