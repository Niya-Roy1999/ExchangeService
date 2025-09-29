package com.example.ExchangeService.ExchangeService.events;

import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderStatusE;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent {
    private String orderId;
    private String userId;
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private Integer quantity;
    private OrderStatusE status;
    private Double price;
    private Double stopPrice;
    private Double trailingOffset;
    private String trailingType;
    private Integer displayQuantity;
    private TimeInForce timeInForce;
}