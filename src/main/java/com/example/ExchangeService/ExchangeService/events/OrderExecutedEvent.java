package com.example.ExchangeService.ExchangeService.events;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class OrderExecutedEvent {

    private String orderId;
    private String counterOrderId;
    private String userId;
    private String symbol;
    private String side;
    private String type;
    private int quantity;
    private BigDecimal price;
    private BigDecimal notionalValue;
    private String status;
    private Instant executedAt;
}
