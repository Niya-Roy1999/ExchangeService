package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.time.Instant;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "orderType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = MarketOrderPlacedEvent.class, name = "MARKET"),
        @JsonSubTypes.Type(value = LimitOrderPlacedEvent.class, name = "LIMIT"),
        @JsonSubTypes.Type(value = StopLossOrderPlacedEvent.class, name = "STOP_MARKET"),
        @JsonSubTypes.Type(value = StopLimitOrderPlacedEvent.class, name = "STOP_LIMIT"),
        @JsonSubTypes.Type(value = TrailingStopOrderPlacedEvent.class, name = "TRAILING_STOP"),
        @JsonSubTypes.Type(value = IcebergOrderPlacedEvent.class, name = "ICEBERG"),
        @JsonSubTypes.Type(value = OCOOrderPlacedEvent.class, name = "ONE_CANCELS_OTHER")
})
@Data
public abstract class BaseOrderPlacedEvent {
    private String orderId;
    private String userId;
    private String symbol;
    private OrderSide side;
    private OrderType orderType;
    private Integer quantity;
    private TimeInForce timeInForce;
    private Instant timestamp;
}