package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import com.example.ExchangeService.ExchangeService.enums.OrderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLossOrder extends BaseOrder{
    private BigDecimal stopPrice;
    public StopLossOrder() {
        super(OrderType.STOP_MARKET);
    }
}
