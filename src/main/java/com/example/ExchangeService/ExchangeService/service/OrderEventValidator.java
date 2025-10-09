package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent.*;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderEventValidator {

    public void validate(BaseOrderPlacedEvent event) throws ValidationException {
        // Common validation
        validateCommonFields(event);
        log.info("Inside the validator for base order placed event:");

        // Type-specific validation
        if (event instanceof LimitOrderPlacedEvent) {
            validateLimitOrder((LimitOrderPlacedEvent) event);
        } else if (event instanceof StopLossOrderPlacedEvent) {
            validateStopLossOrder((StopLossOrderPlacedEvent) event);
        } else if (event instanceof StopLimitOrderPlacedEvent) {
            validateStopLimitOrder((StopLimitOrderPlacedEvent) event);
        }  else if (event instanceof IcebergOrderPlacedEvent) {
            validateIcebergOrder((IcebergOrderPlacedEvent) event);
        } else if (event instanceof MarketOrderPlacedEvent) {
            validateMarketOrder((MarketOrderPlacedEvent) event);
        }
    }

    private void validateCommonFields(BaseOrderPlacedEvent event) throws ValidationException {
        if (event.getOrderId() == null || event.getOrderId().isEmpty()) {
            throw new ValidationException("Order ID is required");
        }
        if (event.getUserId() == null || event.getUserId().isEmpty()) {
            throw new ValidationException("User ID is required");
        }
        if (event.getSymbol() == null || event.getSymbol().isEmpty()) {
            throw new ValidationException("Symbol is required");
        }
        if (event.getSide() == null) {
            throw new ValidationException("Order side is required");
        }
        if (event.getQuantity() == null || event.getQuantity() <= 0) {
            throw new ValidationException("Quantity must be positive");
        }
    }

    private void validateLimitOrder(LimitOrderPlacedEvent event) throws ValidationException {
        if (event.getLimitPrice() == null || event.getLimitPrice() <= 0) {
            throw new ValidationException("Limit order must have a valid price");
        }
    }

    private void validateStopLossOrder(StopLossOrderPlacedEvent event) throws ValidationException {
        if (event.getStopPrice() == null || event.getStopPrice() <= 0) {
            throw new ValidationException("Stop loss order must have a valid stop price");
        }
    }

    private void validateStopLimitOrder(StopLimitOrderPlacedEvent event) throws ValidationException {
        if (event.getStopPrice() == null || event.getStopPrice() <= 0) {
            throw new ValidationException("Stop limit order must have a valid stop price");
        }
        if (event.getLimitPrice() == null || event.getLimitPrice() <= 0) {
            throw new ValidationException("Stop limit order must have a valid limit price");
        }

        // For sell orders: stop price should be <= limit price
        // For buy orders: stop price should be >= limit price
        if (event.getSide() == OrderSide.SELL && event.getStopPrice() > event.getLimitPrice()) {
            throw new ValidationException("For sell stop-limit: stop price must be <= limit price");
        }
        if (event.getSide() == OrderSide.BUY && event.getStopPrice() < event.getLimitPrice()) {
            throw new ValidationException("For buy stop-limit: stop price must be >= limit price");
        }
    }

    private void validateIcebergOrder(IcebergOrderPlacedEvent event) throws ValidationException {
        if (event.getPrice() == null || event.getPrice() <= 0) {
            throw new ValidationException("Iceberg order must have a valid price");
        }
        if (event.getDisplayQuantity() == null || event.getDisplayQuantity() <= 0) {
            throw new ValidationException("Iceberg order must have valid display quantity");
        }
        if (event.getDisplayQuantity() > event.getQuantity()) {
            throw new ValidationException("Display quantity cannot exceed total quantity");
        }
    }

    private void validateMarketOrder(MarketOrderPlacedEvent event) throws ValidationException {
        // Market orders should not have IOC or FOK for most exchanges
        if (event.getTimeInForce() == TimeInForce.GOOD_TILL_DATE) {
            log.warn("Market order with GTD is unusual");
        }
    }

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
