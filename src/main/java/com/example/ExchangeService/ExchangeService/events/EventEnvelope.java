package com.example.ExchangeService.ExchangeService.events;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEnvelope<T> {
    private String eventType;
    private String schemaVersion;
    private String correlationId;
    private String producer;
    private Instant timeStamp;
    private T payload;
}