package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import com.example.ExchangeService.ExchangeService.enums.TrailingType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TrailingStopOrderPlacedEvent extends BaseOrderPlacedEvent {
    private Double trailingOffset;
    private TrailingType trailingType; // PERCENTAGE or ABSOLUTE
    private Double activationPrice; // Optional: price at which trailing starts
}
