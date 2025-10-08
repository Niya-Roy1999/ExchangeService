package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IcebergOrderPlacedEvent extends BaseOrderPlacedEvent {
    private Double price;
    private Integer displayQuantity; // Visible portion
    // Total quantity is in base class
}
