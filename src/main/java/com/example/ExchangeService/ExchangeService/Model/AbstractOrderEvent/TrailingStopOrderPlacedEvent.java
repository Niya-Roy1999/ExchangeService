package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TrailingStopOrderPlacedEvent extends BaseOrderPlacedEvent {
    private Double stopPrice;        // Initial stop price (optional)
    private Double trailAmount;      // Fixed trail amount
    private Double trailPercent;
}
