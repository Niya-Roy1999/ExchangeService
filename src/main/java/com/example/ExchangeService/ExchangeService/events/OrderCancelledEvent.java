package com.example.ExchangeService.ExchangeService.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {

    private String orderId;
    private String userId;
    private String symbol;
    private String side;
    private String type;
    private int quantity;
    private String status;
    private String reason;
    private Instant cancelledAt;
}
