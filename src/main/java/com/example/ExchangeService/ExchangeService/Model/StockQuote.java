package com.example.ExchangeService.ExchangeService.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockQuote {

    private String symbol;
    private double currentPrice;
    private double change;
    private double changePercent;
    private double highPrice;
    private double lowPrice;
    private double openPrice;
    private double previousClose;
    private long timeStamp;
}

