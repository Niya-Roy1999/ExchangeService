package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Handles the execution of trades between orders.
 * Single Responsibility Principle: Handles only trade execution logic.
 */
@Component
@Slf4j
public class TradeExecutor {

    /**
     * Executes a trade between two orders
     */
    public Execution executeTrade(BaseOrder incoming, BaseOrder existing, int quantity, BigDecimal executionPrice) {
        log.info("Executing trade: {} units between {} and {}",
                quantity, incoming.getOrderId(), existing.getOrderId());

        incoming.setFilledQuantity(incoming.getFilledQuantity() + quantity);
        existing.setFilledQuantity(existing.getFilledQuantity() + quantity);

        Execution execution = Execution.builder()
                .orderId(Long.parseLong(incoming.getOrderId()))
                .counterOrderId(Long.parseLong(existing.getOrderId()))
                .userId(incoming.getUserId())
                .instrumentSymbol(incoming.getInstrumentId())
                .side(incoming.getOrderSide().name())
                .quantity(BigDecimal.valueOf(quantity))
                .price(executionPrice)
                .notional(executionPrice.multiply(BigDecimal.valueOf(quantity)))
                .executedAt(LocalDateTime.now())
                .build();

        log.info("Trade executed: {} units between Order {} ({} - {}) and Order {} ({} - {}) at price {}",
                quantity,
                incoming.getOrderId(), incoming.getOrderSide(), incoming.getUserId(),
                existing.getOrderId(), existing.getOrderSide(), existing.getUserId(),
                executionPrice
        );

        return execution;
    }

    /**
     * Copies fields from source order to target order
     */
    public void copyBaseOrderFields(BaseOrder source, BaseOrder target) {
        target.setOrderId(source.getOrderId());
        target.setUserId(source.getUserId());
        target.setInstrumentId(source.getInstrumentId());
        target.setOrderSide(source.getOrderSide());
        target.setOrderType(source.getOrderType());
        target.setQuantity(source.getQuantity());
        target.setFilledQuantity(source.getFilledQuantity());
        target.setTimeInForce(source.getTimeInForce());
        target.setTimestamp(source.getTimestamp());
        target.setGoodTillDate(source.getGoodTillDate());
        target.setExpiryTime(source.getExpiryTime());
    }
}
