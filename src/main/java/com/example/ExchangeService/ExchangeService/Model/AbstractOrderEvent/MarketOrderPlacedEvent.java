package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MarketOrderPlacedEvent extends BaseOrderPlacedEvent {
    // Market orders only need base fields
    // No price needed as they execute at best available price
}