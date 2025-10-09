package com.example.ExchangeService.ExchangeService.controller;

import com.example.ExchangeService.ExchangeService.Model.StockQuote;
import com.example.ExchangeService.ExchangeService.service.FinnhubService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stocks")
public class StockSSEController {

    // Working on new-design branch
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
                    List<StockQuote> stockQuotes = new ArrayList<>();
                    // Fetch quotes for all predefined symbols
                    List<Mono<StockQuote>> monos = symbols.stream()
                            .map(finnhubService::getStockQuote)
                            .toList();
                    Mono.zip(monos, results -> {
                        for (Object obj : results) {
                            stockQuotes.add((StockQuote) obj);
                        }
                        return stockQuotes;
                    }).block(); // Blocking here is fine in background thread
                    // Send all quotes as a single SSE event
                    emitter.send(stockQuotes, MediaType.APPLICATION_JSON);
                    Thread.sleep(5000); // update every 5 seconds
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
