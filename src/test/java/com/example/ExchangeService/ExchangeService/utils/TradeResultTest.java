package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.entities.Order;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TradeResultTest {

    private Order orderA;
    private Order orderB;

    @BeforeEach
    void setup() {
        orderA = Order.builder()
                .orderId("1")
                .userId("user1")
                .instrumentId("AAPL")
                .orderSide(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .quantity(100)
                .price(new BigDecimal("150.00"))
                .timeStamp(Instant.now())
                .build();

        orderB = Order.builder()
                .orderId("2")
                .userId("user2")
                .instrumentId("AAPL")
                .orderSide(OrderSide.SELL)
                .orderType(OrderType.LIMIT)
                .quantity(100)
                .price(new BigDecimal("150.00"))
                .timeStamp(Instant.now())
                .build();
    }

    @Test
    void testGetOtherOrder_ReturnsOther() {
        TradeResult trade = TradeResult.builder()
                .execution(new Execution())
                .ordersInvolved(List.of(orderA, orderB))
                .build();
        assertEquals(orderB, trade.getOtherOrder(orderA));
        assertEquals(orderA, trade.getOtherOrder(orderB));
    }

    @Test
    void testGetOtherOrder_ThrowsWhenNullList() {
        TradeResult trade = TradeResult.builder()
                .execution(new Execution())
                .ordersInvolved(null)
                .build();

        assertThrows(IllegalArgumentException.class, () -> trade.getOtherOrder(orderA));
    }

    @Test
    void testGetOtherOrder_ThrowsWhenOneOrder() {
        TradeResult trade = TradeResult.builder()
                .execution(new Execution())
                .ordersInvolved(List.of(orderA))
                .build();

        assertThrows(IllegalArgumentException.class, () -> trade.getOtherOrder(orderA));
    }

    @Test
    void testGetOtherOrder_ThrowsWhenMoreThanTwoOrders() {
        TradeResult trade = TradeResult.builder()
                .execution(new Execution())
                .ordersInvolved(List.of(orderA, orderB, new Order()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> trade.getOtherOrder(orderA));
    }

}
