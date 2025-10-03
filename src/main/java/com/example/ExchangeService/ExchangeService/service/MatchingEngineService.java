package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.entities.Order;
import com.example.ExchangeService.ExchangeService.Repositories.ExecutionRepository;
import com.example.ExchangeService.ExchangeService.Repositories.OrderStatusRepository;
import com.example.ExchangeService.ExchangeService.Repositories.ProcessedEventsRepository;
import com.example.ExchangeService.ExchangeService.entities.OrderStatus;
import com.example.ExchangeService.ExchangeService.entities.ProcessedEvent;
import com.example.ExchangeService.ExchangeService.enums.OrderStatusE;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import com.example.ExchangeService.ExchangeService.events.OrderPlacedEvent;
import java.time.Instant;
import com.example.ExchangeService.ExchangeService.utils.OrderBook;
import com.example.ExchangeService.ExchangeService.utils.OrderBookManager;
import com.example.ExchangeService.ExchangeService.utils.TradeResult;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
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

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    public Order createOrderFromEvent(OrderPlacedEvent event) {
        Order order = new Order();
        order.setOrderId(event.getOrderId());
        order.setUserId(event.getUserId());
        order.setInstrumentId(event.getSymbol());
        order.setOrderSide(event.getSide());
        order.setOrderType(event.getType());
        order.setTimeInForce(event.getTimeInForce());
        order.setQuantity(event.getQuantity());
        order.setPrice(event.getPrice() != null ? BigDecimal.valueOf(event.getPrice()) : null);
        order.setStopPrice(event.getStopPrice() != null ? BigDecimal.valueOf(event.getStopPrice()) : null);
        order.setTrailingOffset(event.getTrailingOffset() != null ? BigDecimal.valueOf(event.getTrailingOffset()) : null);
        order.setTrailingType(event.getTrailingType());
        order.setDisplayQuantity(event.getDisplayQuantity() != null ? event.getDisplayQuantity() : event.getQuantity());

        if(event.getTimeInForce() != null) {
            order.setExpiryTime(event.getTimeInForce() == TimeInForce.GOOD_TILL_DATE
            ? Instant.now().plus(1, ChronoUnit.HOURS) : null);
        }
        order.setTimeStamp(Instant.now());
        return order;
    }

    @Transactional
    public void process(String eventId, OrderPlacedEvent event) {
        // Idempotency check
        if (eventsRepo.existsById(eventId)) {
            log.warn("Duplicate event {} skipped", eventId);
            return;
        }
        // Convert event -> Order domain object
        Order order = createOrderFromEvent(event);
        String symbol = event.getSymbol();
        OrderBook orderBook = orderBookManager.getOrderBook(symbol);
        List<TradeResult> tradeResults = orderBook.addOrder(order);

        for(TradeResult result: tradeResults) {
            Execution execution = result.getExecution();

            for(Order o: result.getOrdersInvolved()) {
                OrderStatus status = new OrderStatus();
                status.setOrderId(Long.parseLong(o.getOrderId()));

                OrderStatusE orderStatus;
                if(execution != null) {
                    orderStatus = determineOrderStatus(o);
                    execRepo.save(execution);
                    kafkaProducerService.publishOrderExecution(o, execution);
                } else {
                    orderStatus = OrderStatusE.CANCELLED;
                    kafkaProducerService.publishOrderCancellation(o, "There is some issue in this ");
                }

                status.setStatus(orderStatus);
                status.setFilledQuantity(BigDecimal.valueOf(o.getFilledQuantity()));
                status.setUpdatedAt(Instant.now());
                statusRepo.save(status);

                log.info("Order {} status updated to {} (filled: {}/{})",
                        o.getOrderId(), orderStatus, o.getFilledQuantity(), o.getQuantity());
            }
        }
        eventsRepo.save(new ProcessedEvent(eventId, Instant.now()));
    }

    OrderStatusE determineOrderStatus(Order order) {
        if (order.getFilledQuantity() == 0) {
            return OrderStatusE.PENDING;
        } else if (order.getFilledQuantity() < order.getQuantity()) {
            return OrderStatusE.PARTIALLY_FILLED;
        } else {
            return OrderStatusE.FILLED;
        }
    }
}


