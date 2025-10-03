package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.Repositories.OrderStatusRepository;
import com.example.ExchangeService.ExchangeService.entities.Order;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TimeInForceHandler {

    public boolean validateOrderTIF(Order order) {
        TimeInForce tif = order.getTimeInForce();
        if (tif == null) {
            order.setTimeInForce(TimeInForce.GOOD_TILL_CANCELLED);
            return true;
        }

        switch (tif) {
            case GOOD_TILL_DATE:
                Instant goodTill = order.getGoodTillDate() != null ? order.getGoodTillDate() : null;
                if (goodTill == null) {
                    log.warn("GTD Order {} has no goodTillDate set, rejecting", order.getOrderId());
                    return false;
                }
                if (Instant.now().isAfter(goodTill)) {
                    log.info("GTD order {} expired before processing", order.getOrderId());
                    return false;
                }
                break;
            case IMMEDIATE_OR_CANCEL:
            case DAY:
            case GOOD_TILL_CANCELLED:
            default:
                break;
        }

        return true;
    }

    public boolean validateFOK(Order order, int availableLiquidity) {
        if (order.getTimeInForce() != TimeInForce.FILL_OR_KILL) return true;
        int remainingQuantity = order.getQuantity() - order.getFilledQuantity();

        if (availableLiquidity < remainingQuantity) {
            log.info("FOK order {} cannot be completely filled. Required: {}, Available: {}",
                    order.getOrderId(), remainingQuantity, availableLiquidity);
            return false;
        }
        log.info("FOK order {} can be completely filled", order.getOrderId());
        return true;
    }

    public List<Order> getExpiredOrders(List<Order> orders) {
        List<Order> expiredOrders = new ArrayList<>();
        Instant now = Instant.now();
        for (Order order : orders) {
            if (isOrderExpired(order, now)) expiredOrders.add(order);
        }
        return expiredOrders;
    }

    public boolean isOrderExpired(Order order, Instant currentTime) {
        TimeInForce tif = order.getTimeInForce();
        if (tif == null) return false;

        switch (tif) {
            case GOOD_TILL_DATE:
                return order.getGoodTillDate() != null && currentTime.isAfter(order.getGoodTillDate());
            case DAY:
                return isEndOfTradingDay(order, currentTime);
            case GOOD_TILL_CANCELLED:
            case IMMEDIATE_OR_CANCEL:
            case FILL_OR_KILL:
            default:
                return false;
        }
    }

    private boolean isEndOfTradingDay(Order order, Instant currentTime) {
        Instant expiry = order.getExpiryTime();
        if (expiry == null) return false;
        return currentTime.isAfter(expiry);
    }

    public boolean shouldCancelAfterExecution(Order order, boolean wasPartiallyFilled) {
        TimeInForce timeInForce = order.getTimeInForce();
        if (timeInForce == null) return false;

        switch (timeInForce) {
            case IMMEDIATE_OR_CANCEL:
                if (order.getFilledQuantity() < order.getQuantity()) {
                    log.info("IOC order {} cancelled unfilled portion: {}", order.getOrderId(),
                            order.getQuantity() - order.getFilledQuantity());
                    return true;
                }
                break;
            case FILL_OR_KILL:
                if (order.getFilledQuantity() < order.getQuantity()) {
                    log.error("Fill Or Kill order was not completely filled, cancelling {}", order.getOrderId());
                    return true;
                }
                break;
            case GOOD_TILL_CANCELLED:
            case DAY:
            case GOOD_TILL_DATE:
            default:
                return false;
        }
        return false;
    }

    public TradeResult createCancellationResult(Order order, String reason) {
        log.info("Cancelling order {} due to Time In Force {}: {}", order.getOrderId(), order.getTimeInForce(), reason);
        return new TradeResult(null, List.of(order));
    }
}


