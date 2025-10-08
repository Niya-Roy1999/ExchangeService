package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.Model.*;
import com.example.ExchangeService.ExchangeService.Model.AbstractOrder.BaseOrder;
import com.example.ExchangeService.ExchangeService.Model.AbstractOrderEvent.BaseOrderPlacedEvent;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.Repositories.ExecutionRepository;
import com.example.ExchangeService.ExchangeService.Repositories.OrderStatusRepository;
import com.example.ExchangeService.ExchangeService.Repositories.ProcessedEventsRepository;
import com.example.ExchangeService.ExchangeService.entities.OrderStatus;
import com.example.ExchangeService.ExchangeService.entities.ProcessedEvent;
import com.example.ExchangeService.ExchangeService.enums.OrderStatusE;
import java.time.Instant;
import com.example.ExchangeService.ExchangeService.utils.OrderBook;
import com.example.ExchangeService.ExchangeService.utils.OrderBookManager;
import com.example.ExchangeService.ExchangeService.utils.OrderMapper;
import com.example.ExchangeService.ExchangeService.utils.TradeResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingEngineService {

    private final ExecutionRepository execRepo;
    private final OrderStatusRepository statusRepo;
    private final ProcessedEventsRepository eventsRepo;
    private final ExecutionEventService kafkaProducerService;
    private final OrderBookManager orderBookManager;
    private final OrderMapper orderMapper;

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    @Transactional
    public void process(String eventId, BaseOrderPlacedEvent event) {
        // 1️⃣ Idempotency check
        if (eventsRepo.existsById(eventId)) {
            log.warn("Duplicate event {} skipped", eventId);
            return;
        }

        try {
            // 2️⃣ Map event -> typed domain order
            BaseOrder domainOrder = orderMapper.mapToDomainOrder(event);
            // 3️⃣ Get the order book for the symbol
            String symbol = event.getSymbol();
            OrderBook orderBook = orderBookManager.getOrderBook(symbol);
            // 4️⃣ Add order to the order book and execute trades
            List<TradeResult> tradeResults = orderBook.addOrder(domainOrder);
            // 5️⃣ Persist executions and update order status
            for (TradeResult result : tradeResults) {
                Execution execution = result.getExecution();

                for (BaseOrder o : result.getOrdersInvolved()) {
                    OrderStatus status = new OrderStatus();
                    status.setOrderId(Long.parseLong(o.getOrderId()));

                    OrderStatusE orderStatus;
                    if (execution != null) {
                        orderStatus = determineOrderStatus(o);
                        execRepo.save(execution);
                        kafkaProducerService.publishOrderExecution(o, execution);
                    } else {
                        orderStatus = OrderStatusE.CANCELLED;
                        kafkaProducerService.publishOrderCancellation(o, "Issue occurred in execution");
                    }

                    status.setStatus(orderStatus);
                    status.setFilledQuantity(BigDecimal.valueOf(o.getFilledQuantity()));
                    status.setUpdatedAt(Instant.now());
                    statusRepo.save(status);

                    log.info("Order {} status updated to {} (filled: {}/{})",
                            o.getOrderId(), orderStatus, o.getFilledQuantity(), o.getQuantity());
                }
            }

            // 6️⃣ Mark the event as processed
            eventsRepo.save(new ProcessedEvent(eventId, Instant.now()));

        } catch (Exception e) {
            log.error("Error processing order event {}", eventId, e);
            throw e; // optional: rethrow for transaction rollback
        }
    }

    /**
     * Determine order status based on filled quantity
     */
    private OrderStatusE determineOrderStatus(BaseOrder order) {
        if (order.getFilledQuantity() == 0) {
            return OrderStatusE.PENDING;
        } else if (order.getFilledQuantity() < order.getQuantity()) {
            return OrderStatusE.PARTIALLY_FILLED;
        } else {
            return OrderStatusE.FILLED;
        }
    }
}


