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
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
// domain/MatchingEngine.java

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

        BigDecimal qty   = evt.getTotalQuantity();

        BigDecimal notional = price.multiply(qty);

        // Save execution

        Execution exec = execRepo.save(Execution.builder()

                .orderId(Long.valueOf(evt.getOrderId()))

                .userId(Long.valueOf(evt.getUserId()))

                .instrumentSymbol(evt.getInstrumentSymbol())

                .side(evt.getOrderSide())

                .quantity(qty)

                .price(price)

                .notional(notional)

                .executedAt(LocalDateTime.now())

                .build());

        // Update order status

        OrderStatus status = OrderStatus.builder()

                .orderId(Long.valueOf(evt.getOrderId()))

                .status("FILLED")

                .filledQuantity(qty)

                .updatedAt(LocalDateTime.now())

                .build();

        statusRepo.save(status);

        // Mark event processed

        eventsRepo.save(new ProcessedEvent(eventId, LocalDateTime.now()));

        log.info("Order {} executed: {} {} @ {}",

                evt.getOrderId(), qty, evt.getInstrumentSymbol(), price);

    }

}


