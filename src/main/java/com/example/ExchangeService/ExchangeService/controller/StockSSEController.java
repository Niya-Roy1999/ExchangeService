package com.example.ExchangeService.ExchangeService.controller;

import com.example.ExchangeService.ExchangeService.service.FinnhubService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stocks")
public class StockSSEController {

    private final FinnhubService finnhubService;
    private final List<String> symbols;

    public StockSSEController(FinnhubService finnhubService,
                              @Value("${stocks.symbols}") String symbolsStr) {
        this.finnhubService = finnhubService;
        this.symbols = Arrays.stream(symbolsStr.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }

    @GetMapping("/quote/stream")
    public SseEmitter streamStockQuotes() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                while (true) {
                    Map<String, Object> quotesMap = new HashMap<>();
                    // Fetch quotes for all predefined symbols
                    for (String symbol : symbols) {
                        String quoteJson = finnhubService.getStockQuote(symbol).block();
                        quotesMap.put(symbol, quoteJson);
                    }
                    // Send all quotes as a single SSE event
                    emitter.send(quotesMap, MediaType.APPLICATION_JSON);
                    Thread.sleep(5000); // update every 5 seconds
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
