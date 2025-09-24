package com.example.ExchangeService.ExchangeService.kafka;

import com.example.ExchangeService.ExchangeService.events.EventEnvelope;
import com.example.ExchangeService.ExchangeService.events.OrderPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class OrdersConsumer {
    @KafkaListener(topics = "orders.v1", groupId = "exchange-service")
    public void onMessage(EventEnvelope<OrderPlacedEvent> envelope) {
        log.info("Got {} v{} from {} id={}",
                envelope.getEventType(), envelope.getVersion(), envelope.getSource(), envelope.getEventId());
        OrderPlacedEvent evt = envelope.getPayload();
        Long orderId = Long.valueOf(evt.getOrderId());
        Long userId  = Long.valueOf(evt.getUserId());
        BigDecimal qty = evt.getTotalQuantity();
        BigDecimal notional = new BigDecimal(evt.getNotionalValue());
        // TODO: pass to matching engine / persist execution / publish follow-ups
        log.info("Order {}: {} {} notional={}",
                orderId, evt.getInstrumentSymbol(), evt.getOrderSide(), notional);
    }
}