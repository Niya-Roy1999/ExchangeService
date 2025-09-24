package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.entities.ProcessedEvent;
import com.example.ExchangeService.ExchangeService.entities.OrderStatus;
import com.example.ExchangeService.ExchangeService.Repositories.ExecutionRepository;
import com.example.ExchangeService.ExchangeService.Repositories.OrderStatusRepository;
import com.example.ExchangeService.ExchangeService.Repositories.ProcessedEventsRepository;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.events.OrderPlacedEvent;
import java.time.LocalDateTime;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j

public class MatchingEngine {

    private final ExecutionRepository execRepo;
    private final OrderStatusRepository statusRepo;
    private final ProcessedEventsRepository eventsRepo;

    @Transactional
    public void process(String eventId, OrderPlacedEvent evt) {
        // Idempotency check
        if (eventsRepo.existsById(eventId)) {
            log.warn("Duplicate event {} skipped", eventId);
            return;
        }

        // Simplified: MARKET order always fills at fixed reference price
        BigDecimal price = BigDecimal.valueOf(175.50); // TODO: hook to real price source
        Integer qty   = evt.getQuantity();
        BigDecimal notional =  price.multiply(BigDecimal.valueOf(qty));
        // Save execution
        Execution exec = execRepo.save(Execution.builder()
                .orderId(Long.valueOf(evt.getOrderId()))
                .userId(Long.valueOf(evt.getUserId()))
                .instrumentSymbol(evt.getSymbol())
                .side(evt.getSide())
                .quantity(BigDecimal.valueOf(qty))
                .price(price)
                .notional(notional)
                .executedAt(LocalDateTime.now())
                .build());

        // Update order status
        OrderStatus status = OrderStatus.builder()
                .orderId(Long.valueOf(evt.getOrderId()))
                .status("FILLED")
                .filledQuantity(BigDecimal.valueOf(qty))
                .updatedAt(LocalDateTime.now())
                .build();

        statusRepo.save(status);
        // Mark event processed
        eventsRepo.save(new ProcessedEvent(eventId, LocalDateTime.now()));
        log.info("Order {} executed: {} {} @ {}",
                evt.getOrderId(), qty, evt.getSymbol(), price);
    }
}


