package com.example.ExchangeService.ExchangeService.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages OrderBook instances for different trading symbols.
 * Creates OrderBook instances with all required dependencies.
 * Each symbol gets its own set of components to maintain isolation.
 */
@Component
@RequiredArgsConstructor
public class OrderBookManager {

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final TimeInForceHandler timeInForceHandler;
    private final TradeExecutor tradeExecutor;

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, k -> {
            // Create separate component instances for each symbol
            OrderQueue orderQueue = new OrderQueue();
            TrailingStopManager trailingStopManager = new TrailingStopManager();
            StopOrderManager stopOrderManager = new StopOrderManager(tradeExecutor, trailingStopManager);
            OCOOrderManager ocoOrderManager = new OCOOrderManager();
            OrderMatcher orderMatcher = new OrderMatcher(tradeExecutor, orderQueue);

            OrderBook orderBook = new OrderBook(
                orderQueue,
                stopOrderManager,
                ocoOrderManager,
                trailingStopManager,
                orderMatcher,
                tradeExecutor
            );
            orderBook.setTimeInForceHandler(timeInForceHandler);
            return orderBook;
        });
    }
}
