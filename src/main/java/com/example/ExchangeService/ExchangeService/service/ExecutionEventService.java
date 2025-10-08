package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.enums.OrderStatusE;
import com.example.ExchangeService.ExchangeService.events.EventEnvelope;
import com.example.ExchangeService.ExchangeService.events.OrderCancelledEvent;
import com.example.ExchangeService.ExchangeService.events.OrderExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionEventService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String Order_Topic = "execution.v1";
    private static final String Failed_Order_Topic = "failed.v1";

    public void publishOrderExecution(BaseOrder order, Execution execution) {

        if(execution == null) {
            throw new IllegalArgumentException("Exception cannot be null for trade event");
        }
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        // Determining counterOrderId for this order
        String counterId = order.getOrderId().equals(String.valueOf(execution.getOrderId()))
                ? String.valueOf(execution.getCounterOrderId())
                : String.valueOf(execution.getOrderId());

        OrderExecutedEvent payload = OrderExecutedEvent.builder()
                .orderId(order.getOrderId())
                .counterOrderId(counterId)
                .userId(order.getUserId())
                .symbol(order.getInstrumentId())
                .side(order.getOrderSide().name())
                .type(order.getOrderType().name())
                .quantity(order.getQuantity())
                .price(execution.getPrice())
                .notionalValue(execution.getPrice().multiply(BigDecimal.valueOf(order.getFilledQuantity())))
                .status(getStatusString(order))
                .executedAt(Instant.now())
                .build();

        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .eventType("OrderStatusUpdated")
                .schemaVersion("v1")
                .correlationId(UUID.randomUUID().toString())
                .producer("exchange-service")
                .payload(payload)
                .timeStamp(Instant.now())
                .build();

        kafkaTemplate.send(Order_Topic, envelope);
        log.info("Published OrderExecutionEvent to Kafka: {}", envelope);
    }

    public void publishOrderCancellation(BaseOrder order, String reason) {
        OrderCancelledEvent payload = OrderCancelledEvent.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .symbol(order.getInstrumentId())
                .side(order.getOrderSide().name())
                .type(order.getOrderType().name())
                .quantity(order.getQuantity())
                .status("CANCELLED")
                .reason(reason)
                .cancelledAt(Instant.now())
                .build();

        EventEnvelope<Object> envelope = EventEnvelope.builder()
                .eventType("OrderCancelled")
                .schemaVersion("v1")
                .correlationId(UUID.randomUUID().toString())
                .producer("exchange-service")
                .payload(payload)
                .timeStamp(Instant.now())
                .build();

        kafkaTemplate.send(Failed_Order_Topic, envelope);
        log.info("🚫 Published OrderCancelledEvent to Kafka: {}", envelope);
    }

    private String getStatusString(BaseOrder order) {
        return getOrderStatus(order).name();
    }

    private OrderStatusE getOrderStatus(BaseOrder order) {
        if (order.getFilledQuantity() == 0) {
            return OrderStatusE.PENDING;
        } else if (order.getFilledQuantity() < order.getQuantity()) {
            return OrderStatusE.PARTIALLY_FILLED;
        } else {
            return OrderStatusE.FILLED;
        }
    }
}
