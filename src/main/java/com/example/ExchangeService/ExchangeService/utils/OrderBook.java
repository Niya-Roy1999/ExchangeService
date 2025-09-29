package com.example.ExchangeService.ExchangeService.utils;

import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.entities.Order;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Slf4j
public class OrderBook {

    private PriorityQueue<Order> buyOrders = new PriorityQueue<>(
            (o1, o2) -> {
                int cmp = o2.getPrice().compareTo(o1.getPrice());
                return cmp != 0 ? cmp : o1.getTimeStamp().compareTo(o2.getTimeStamp());
            });

    private PriorityQueue<Order> sellOrders = new PriorityQueue<>(
            Comparator.comparing(Order::getPrice).thenComparing(Order::getTimeStamp));

    private List<Order> stopOrders = new ArrayList<>();
    private BigDecimal lastTradedPrice = BigDecimal.ZERO;

    //Adding an order in the order book
    public List<TradeResult> addOrder(Order order) {
        log.info("Adding order:  {}", order);
        //Creating an empty Executions ArrayList;
        List<TradeResult> tradeResults = new ArrayList<>();
        //Checking for any stop order
        if(order.getOrderType() == OrderType.STOP_MARKET || order.getOrderType() == OrderType.STOP_LIMIT) {
            stopOrders.add(order);
            return tradeResults;
        }

        //Checking for Opposite heap, if order is buy then check in sellOrders and vice verse
        PriorityQueue<Order> opposite = (order.getOrderSide() == OrderSide.BUY) ? sellOrders : buyOrders;
        while(!opposite.isEmpty() && order.getQuantity() > order.getFilledQuantity()) {
            Order bestOrder = opposite.peek();
            //Calculating how much trade (quantities) can be executed
            int tradableQuantity = Math.min(bestOrder.getQuantity() - bestOrder.getFilledQuantity(),
                    order.getQuantity() - order.getFilledQuantity());
            Execution execution = executeTrade(order, bestOrder, tradableQuantity);
            tradeResults.add(new TradeResult(execution, List.of(order, bestOrder)));
            if(bestOrder.getFilledQuantity() == bestOrder.getQuantity()) opposite.poll();
            tradeResults.addAll(checkStopOrders());
        }
        //Add remaining order to its own heap
        if(order.getQuantity() > order.getFilledQuantity()) {
            if(order.getOrderSide() == OrderSide.BUY) buyOrders.add(order);
            else sellOrders.add(order);
        }
        printOrderBookState();
        return tradeResults;
    }

    private void printOrderBookState() {
        log.info("===== ORDER BOOK STATE =====");
        if (!buyOrders.isEmpty()) {
            log.info("BUY Orders:");
            buyOrders.forEach(o -> log.info("{}", o));
        }
        if (!sellOrders.isEmpty()) {
            log.info("SELL Orders:");
            sellOrders.forEach(o -> log.info("{}", o));
        }
        if (!stopOrders.isEmpty()) {
            log.info("STOP Orders:");
            stopOrders.forEach(o -> log.info("{}", o));
        }
        log.info("Last Traded Price: {}", lastTradedPrice);
        log.info("============================");
    }

    private List<TradeResult> checkStopOrders() {
        List<TradeResult> triggeredResult = new ArrayList<>();
        Iterator<Order> iterator = stopOrders.iterator();

        while(iterator.hasNext()) {
            Order stopOrder = iterator.next();
            boolean triggered = false;

            if(stopOrder.getOrderSide() == OrderSide.BUY && lastTradedPrice != null && stopOrder.getStopPrice() != null &&
                    lastTradedPrice.compareTo(stopOrder.getStopPrice()) >= 0) triggered = true;
            else if(stopOrder.getOrderSide() == OrderSide.SELL && lastTradedPrice != null && stopOrder.getStopPrice() != null &&
                    lastTradedPrice.compareTo(stopOrder.getStopPrice()) <= 0) triggered = true;

            if(triggered) {
                log.info("Stop order has been triggered: {}", stopOrder);
                iterator.remove();
                if(stopOrder.getOrderType() == OrderType.STOP_MARKET) stopOrder.setOrderType(OrderType.MARKET);
                else stopOrder.setOrderType(OrderType.LIMIT);

                triggeredResult.addAll(addOrder(stopOrder));
            }
        } return triggeredResult;
    }

    private Execution executeTrade(Order incoming, Order existing, int quantity) {
        log.info("Executing trade: {} units between {} and {}", quantity, incoming.getOrderId(), existing.getOrderId());
        incoming.setFilledQuantity(incoming.getFilledQuantity() + quantity);
        existing.setFilledQuantity(existing.getFilledQuantity() + quantity);

        BigDecimal executionPrice = existing.getPrice();
        lastTradedPrice = executionPrice;
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
}
