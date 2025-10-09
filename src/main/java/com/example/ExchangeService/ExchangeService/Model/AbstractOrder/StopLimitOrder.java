package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import com.example.ExchangeService.ExchangeService.enums.OrderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLimitOrder extends BaseOrder{
    private BigDecimal limitPrice;
    private BigDecimal stopPrice;

    public StopLimitOrder() {
        super(OrderType.STOP_LIMIT);
    }
}
