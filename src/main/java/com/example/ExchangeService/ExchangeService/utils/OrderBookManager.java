package com.example.ExchangeService.ExchangeService.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OrderBookManager {

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    @Autowired
    private TimeInForceHandler timeInForceHandler;

    public OrderBook getOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, k -> {
            OrderBook orderBook = new OrderBook();
            orderBook.setTimeInForceHandler(timeInForceHandler);
            return orderBook;
        });
    }
}
