package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.*;
import com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent.*;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class OrderMapper {

    public BaseOrder mapToDomainOrder(BaseOrderPlacedEvent event) {
        switch (event.getOrderType()) {
            case MARKET -> {
                MarketOrderPlacedEvent e = (MarketOrderPlacedEvent) event;
                MarketOrder order = new MarketOrder();
                setBaseFields(order, e);
                return order;
            }
            case LIMIT -> {
                LimitOrderPlacedEvent e = (LimitOrderPlacedEvent) event;
                LimitOrder order = new LimitOrder();
                setBaseFields(order, e);
                order.setPrice(BigDecimal.valueOf(e.getLimitPrice()));
                return order;
            }
            case STOP_MARKET -> {
                StopLossOrderPlacedEvent e = (StopLossOrderPlacedEvent) event;
                StopLossOrder order = new StopLossOrder();
                setBaseFields(order, e);
                order.setStopPrice(BigDecimal.valueOf(e.getStopPrice()));
                return order;
            }
            case STOP_LIMIT -> {
                StopLimitOrderPlacedEvent e = (StopLimitOrderPlacedEvent) event; // cast correctly
                StopLimitOrder order = new StopLimitOrder();                      // create correct domain object
                setBaseFields(order, e);
                order.setStopPrice(BigDecimal.valueOf(e.getStopPrice()));         // set stop price
                order.setLimitPrice(BigDecimal.valueOf(e.getLimitPrice()));       // set limit price
                return order;
            }
            case TRAILING_STOP -> {
                TrailingStopOrderPlacedEvent e = (TrailingStopOrderPlacedEvent) event;
                return mapToTrailingStopOrder(e);
            }
            // add other types similarly
            default -> throw new IllegalArgumentException("Unknown order type: " + event.getOrderType());
        }
    }

    private TrailingStopOrder mapToTrailingStopOrder(TrailingStopOrderPlacedEvent event) {
        TrailingStopOrder order = new TrailingStopOrder();
        setBaseFields(order, event);
        order.setOrderType(OrderType.TRAILING_STOP);

        // Set initial stop price if provided
        if (event.getStopPrice() != null) {
            order.setStopPrice(BigDecimal.valueOf(event.getStopPrice()));
            order.setInitialStopPrice(BigDecimal.valueOf(event.getStopPrice()));
        }

        // Set trail amount or percent (only one should be provided)
        if (event.getTrailAmount() != null) {
            order.setTrailAmount(BigDecimal.valueOf(event.getTrailAmount()));
        } else if (event.getTrailPercent() != null) {
            order.setTrailPercent(BigDecimal.valueOf(event.getTrailPercent()));
        } else {
            throw new IllegalArgumentException(
                    "TrailingStop order must have either trailAmount or trailPercent");
        }

        return order;
    }

    private void setBaseFields(BaseOrder order, BaseOrderPlacedEvent event) {
        order.setOrderId(event.getOrderId());
        order.setUserId(event.getUserId());
        order.setInstrumentId(event.getSymbol());
        order.setOrderSide(event.getSide());
        order.setOrderType(event.getOrderType());
        order.setTimeInForce(event.getTimeInForce());
        order.setQuantity(event.getQuantity());
        order.setTimestamp(Instant.now());
    }
}

