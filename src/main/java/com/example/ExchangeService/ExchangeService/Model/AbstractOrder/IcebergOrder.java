package com.example.ExchangeService.ExchangeService.Model.AbstractOrder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IcebergOrder extends BaseOrder {
    private BigDecimal price;
}
