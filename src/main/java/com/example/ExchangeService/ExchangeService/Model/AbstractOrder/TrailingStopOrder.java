package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TrailingStopOrder extends BaseOrder{

    private BigDecimal stopPrice;
}
