package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import lombok.*;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public class LimitOrder extends BaseOrder{
    private BigDecimal price;
}
