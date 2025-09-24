package com.example.ExchangeService.ExchangeService.events;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
@Data @NoArgsConstructor @AllArgsConstructor
public class EventEnvelope<T> {
    private String eventType;   // e.g. "OrderPlaced"
    private String version;     // e.g. "v1"
    private String eventId;     // UUID
    private String source;      // "order-service"
    private String time;        // ISO-8601 string
    private T payload;          // the actual event
}