package com.example.ExchangeService.ExchangeService.events;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
    private String eventType;
    private String schemaVersion;
    private String correlationId;
    private String producer;
    private String timeStamp;
    private T payload;
}