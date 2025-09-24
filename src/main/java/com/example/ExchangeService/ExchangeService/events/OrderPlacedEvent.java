package com.example.ExchangeService.ExchangeService.events;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent {
    private String orderId;
    private String userId;
    private String symbol;
    private String side;
    private String type;
    private Integer quantity;
    private Double price;
    private Double stopPrice;
    private Double trailingOffset;
    private String trailingType;
    private Integer displayQuantity;
    private String timeInForce;
}