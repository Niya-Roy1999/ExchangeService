package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StopLossOrderPlacedEvent extends BaseOrderPlacedEvent {
    private Double stopPrice; // Trigger price
}
