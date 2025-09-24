package com.example.ExchangeService.ExchangeService.events;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
@Data @NoArgsConstructor @AllArgsConstructor
public class OrderPlacedEvent {
    private String orderId;             // producer sends as String
    private String userId;              // producer sends as String
    private String instrumentSymbol;
    private String orderSide;           // "BUY"/"SELL"
    private String type;                // "MARKET"
    private BigDecimal totalQuantity;   // number
    private String notionalValue;       // "1755.00" as string
    private String advancedFeatures;    // optional
}