package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class LimitOrderPlacedEvent extends BaseOrderPlacedEvent{
    private Double limitPrice; // Required for limit orders
}