package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import com.example.ExchangeService.ExchangeService.enums.OrderType;
import lombok.*;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LimitOrder extends BaseOrder{
    private BigDecimal price;
    public LimitOrder() {
        super(OrderType.LIMIT);
    }
}
