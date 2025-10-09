package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.Model.StockQuote;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class FinnhubService {

    @Value("${finnhub.api.key}")
    private String apiKey;

    private final WebClient webClient = WebClient.create("https://finnhub.io/api/v1");

    public Mono<StockQuote> getStockQuote(String symbol) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/quote")
                        .queryParam("symbol", symbol)
                        .queryParam("token", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(node -> new StockQuote(
                        symbol,
                        node.get("c").asDouble(),
                        node.get("d").asDouble(),
                        node.get("dp").asDouble(),
                        node.get("h").asDouble(),
                        node.get("l").asDouble(),
                        node.get("o").asDouble(),
                        node.get("pc").asDouble(),
                        node.get("t").asLong()
                ));
    }
}
