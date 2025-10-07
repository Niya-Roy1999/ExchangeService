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
                    for (String symbol : symbols) {
                        String quoteJson = finnhubService.getStockQuote(symbol).block();
                        JsonNode node = new ObjectMapper().readTree(quoteJson);
                        StockQuote quote = new StockQuote(
                                symbol,
                                node.get("c").asDouble(),
                                node.get("d").asDouble(),
                                node.get("dp").asDouble(),
                                node.get("h").asDouble(),
                                node.get("l").asDouble(),
                                node.get("o").asDouble(),
                                node.get("pc").asDouble(),
                                node.get("t").asLong()
                        );
                        stockQuotes.add(quote);
                    }
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
