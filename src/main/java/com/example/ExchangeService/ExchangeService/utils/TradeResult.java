package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeResult {

    private Execution execution;
    private List<BaseOrder> ordersInvolved;

    public BaseOrder getOtherOrder(BaseOrder order) {
        if(ordersInvolved == null || ordersInvolved.size() != 2) {
            throw new IllegalArgumentException("TradeResult must have exactly 2 orders");
        } return ordersInvolved.get(0).equals(order) ? ordersInvolved.get(1) : ordersInvolved.get(0);
    }
}
