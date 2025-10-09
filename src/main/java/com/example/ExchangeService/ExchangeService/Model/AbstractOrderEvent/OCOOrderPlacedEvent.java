package com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class OCOOrderPlacedEvent extends BaseOrderPlacedEvent {
    private String ocoGroupId;
    private String primaryOrderType;
    private BigDecimal primaryPrice;
    private BigDecimal primaryStopPrice;

    private String secondaryOrderType;
    private BigDecimal secondaryPrice;
    private BigDecimal secondaryStopPrice;
    private BigDecimal secondaryTrailAmount;
}
