package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StopLimitOrderPlacedEvent extends BaseOrderPlacedEvent {
    private Double stopPrice;  // Trigger price
    private Double limitPrice; // Limit price after trigger
}
