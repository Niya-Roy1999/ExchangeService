package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.events.EventEnvelope;
import com.example.ExchangeService.ExchangeService.events.OrderPlacedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrdersEventConsumer {

    private final MatchingEngineService matchingEngine;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public OrdersEventConsumer(MatchingEngineService matchingEngine) {
        this.matchingEngine = matchingEngine;
    }

    @KafkaListener(
            topics = "orders.v1",
            groupId = "exchange-service",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String key = record.key();
        String message = record.value();
        int partition = record.partition();
        long offset = record.offset();

        log.info("Received message - Key: {}, Partition: {}, Offset: {}",
                key, partition, offset);

        log.debug("Raw message content: {}", message);
        try {
            // Deserialize manually
            EventEnvelope<Object> envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<Object>>() {}
            );

            // Switch by event type
            if (envelope.getEventType().equals("OrderPlaced")) {
                handleOrderPlacedEvent(envelope);
            } else {
                log.warn("Unknown event type: {}", envelope.getEventType());
                handleOrderPlacedEvent(envelope);
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON message: {}", message, e);
        } catch (Exception e) {
            log.error("Error processing message: {}", message, e);
        }
    }
    private void handleOrderPlacedEvent(EventEnvelope<Object> eventEnvelope) {
        try {
            log.info("Raw payload: {}", eventEnvelope.getPayload());

            OrderPlacedEvent orderPayload = objectMapper.convertValue(
                    eventEnvelope.getPayload(),
                    OrderPlacedEvent.class
            );

            log.info("Processing OrderPlaced event - OrderId: {}, UserId: {}, Symbol: {}, Side: {}",
                    orderPayload.getOrderId(),
                    orderPayload.getUserId(),
                    orderPayload.getSymbol(),
                    orderPayload.getSide());
            matchingEngine.process(orderPayload.getOrderId(), orderPayload);
        } catch (Exception e) {
            log.error("Error processing OrderPlaced event", e);
        }
    }
}