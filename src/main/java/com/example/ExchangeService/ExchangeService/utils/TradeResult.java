package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.Model.Order;
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
    private List<Order> ordersInvolved;

    public Order getOtherOrder(Order order) {
        if(ordersInvolved == null || ordersInvolved.size() != 2) {
            throw new IllegalArgumentException("TradeResult must have exactly 2 orders");
        } return ordersInvolved.get(0).equals(order) ? ordersInvolved.get(1) : ordersInvolved.get(0);
    }
}
