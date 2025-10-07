package com.example.ExchangeService.ExchangeService.service;

import com.example.ExchangeService.ExchangeService.Repositories.ExecutionRepository;
import com.example.ExchangeService.ExchangeService.Repositories.OrderStatusRepository;
import com.example.ExchangeService.ExchangeService.Repositories.ProcessedEventsRepository;
import com.example.ExchangeService.ExchangeService.entities.Execution;
import com.example.ExchangeService.ExchangeService.Model.Order;
import com.example.ExchangeService.ExchangeService.entities.OrderStatus;
import com.example.ExchangeService.ExchangeService.entities.ProcessedEvent;
import com.example.ExchangeService.ExchangeService.enums.OrderSide;
import com.example.ExchangeService.ExchangeService.enums.OrderStatusE;
import com.example.ExchangeService.ExchangeService.enums.OrderType;
import com.example.ExchangeService.ExchangeService.enums.TimeInForce;
import com.example.ExchangeService.ExchangeService.events.OrderPlacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingEngineServiceTest {

    @Mock
    private ExecutionRepository execRepo;

    @Mock
    private OrderStatusRepository statusRepo;

    @Mock
    private ProcessedEventsRepository eventsRepo;

    @Mock
    private ExecutionEventService kafkaProducerService;

    @InjectMocks
    private MatchingEngineService matchingEngineService;

    @Captor
    private ArgumentCaptor<Execution> executionCaptor;

    @Captor
    private ArgumentCaptor<OrderStatus> orderStatusCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEvent> processedEventCaptor;

    private OrderPlacedEvent createOrderPlacedEvent(String orderId, String symbol, OrderSide side,
                                                    OrderType type, int quantity, Double price) {
        OrderPlacedEvent event = new OrderPlacedEvent();
        event.setOrderId(orderId);
        event.setUserId("user1");
        event.setSymbol(symbol);
        event.setSide(side);
        event.setType(type);
        event.setQuantity(quantity);
        event.setPrice(price);
        event.setStopPrice(null);
        event.setTrailingOffset(null);
        event.setTrailingType(null);
        event.setDisplayQuantity(quantity);
        event.setTimeInForce(TimeInForce.GOOD_TILL_CANCELLED);
        return event;
    }

    @Nested
    @DisplayName("Order Creation Tests")
    class OrderCreationTests {

        @Test
        @DisplayName("Create Limit Order from Event")
        void testCreateLimitOrderFromEvent() {
            OrderPlacedEvent event = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                    OrderType.LIMIT, 100, 150.50);

            Order order = matchingEngineService.createOrderFromEvent(event);
            assertNotNull(order);
            assertEquals("1", order.getOrderId());
            assertEquals("user1", order.getUserId());
            assertEquals("AAPL", order.getInstrumentId());
            assertEquals(OrderSide.BUY, order.getOrderSide());
            assertEquals(OrderType.LIMIT, order.getOrderType());
            assertEquals(100, order.getQuantity());
            assertEquals(0, new BigDecimal("150.50").compareTo(order.getPrice()));
            assertNull(order.getStopPrice());
            assertEquals(100, order.getDisplayQuantity());
            assertNotNull(order.getTimeStamp());
        }

        @Test
        @DisplayName("Create Market Order from Event")
        void testCreateMarketOrderFromEvent() {
            OrderPlacedEvent event = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                    OrderType.MARKET, 50, null);
            Order order = matchingEngineService.createOrderFromEvent(event);
            assertNotNull(order);
            assertEquals("2", order.getOrderId());
            assertEquals(OrderType.MARKET, order.getOrderType());
            assertEquals(50, order.getQuantity());
            assertNull(order.getPrice());
        }

        @Test
        @DisplayName("Create Stop Market Order from Event")
        void testCreateStopMarketOrderFromEvent() {
            OrderPlacedEvent event = createOrderPlacedEvent("3", "AAPL", OrderSide.BUY,
                    OrderType.STOP_MARKET, 100, null);
            event.setStopPrice(155.00);
            Order order = matchingEngineService.createOrderFromEvent(event);
            assertNotNull(order);
            assertEquals(OrderType.STOP_MARKET, order.getOrderType());
            assertNull(order.getPrice());
            assertEquals(new BigDecimal("155.00").setScale(2), order.getStopPrice().setScale(2));
        }

        @Test
        @DisplayName("Create Order with Good Till Date Time in Force")
        void testCreateOrderWithGoodTillDate() {
            OrderPlacedEvent event = createOrderPlacedEvent("4", "AAPL", OrderSide.BUY,
                    OrderType.LIMIT, 100, 150.00);
            event.setTimeInForce(TimeInForce.GOOD_TILL_DATE);
            Order order = matchingEngineService.createOrderFromEvent(event);
            assertNotNull(order);
            assertNotNull(order.getExpiryTime());
        }

        @Nested
        @DisplayName("Process Event Tests - No Trades")
        class ProcessEventNoTradesTests {

            @Test
            @DisplayName("Process Single Order - No Match")
            void testProcessSingleOrderNoMatch() {
                String eventId = "event-1";
                OrderPlacedEvent event = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                when(eventsRepo.existsById(eventId)).thenReturn(false);
                matchingEngineService.process(eventId, event);
                verify(eventsRepo, times(1)).existsById(eventId);
                verify(execRepo, never()).save(any()); // No execution since no match
                verify(eventsRepo, times(1)).save(processedEventCaptor.capture());

                ProcessedEvent savedEvent = processedEventCaptor.getValue();
                assertEquals(eventId, savedEvent.getEventId());
            }

            @Test
            @DisplayName("Process Duplicate Event - Idempotency")
            void testProcessDuplicateEvent() {
                String eventId = "event-duplicate";
                OrderPlacedEvent event = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                when(eventsRepo.existsById(eventId)).thenReturn(true);
                matchingEngineService.process(eventId, event);
                verify(eventsRepo, times(1)).existsById(eventId);
                verify(execRepo, never()).save(any());
                verify(statusRepo, never()).save(any());
                verify(kafkaProducerService, never()).publishOrderExecution(any(), any());
                verify(eventsRepo, never()).save(any()); // Should not save again
            }
        }

        @Nested
        @DisplayName("Process Event Tests - With Trades")
        class ProcessEventWithTradesTests {

            @Test
            @DisplayName("Process Two Matching Orders - Full Fill")
            void testProcessTwoMatchingOrdersFullFill() {
                String eventId1 = "event-1";
                String eventId2 = "event-2";
                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent sellEvent = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process(eventId1, buyEvent);
                matchingEngineService.process(eventId2, sellEvent);
                verify(execRepo, times(1)).save(executionCaptor.capture());
                verify(statusRepo, times(2)).save(orderStatusCaptor.capture());
                verify(kafkaProducerService, times(2)).publishOrderExecution(any(), any());

                Execution execution = executionCaptor.getValue();
                assertNotNull(execution);
                assertEquals(new BigDecimal("100"), execution.getQuantity());
                assertEquals(new BigDecimal("150.00").setScale(2), execution.getPrice().setScale(2));
                List<OrderStatus> statuses = orderStatusCaptor.getAllValues();
                assertEquals(2, statuses.size());
                assertTrue(statuses.stream().allMatch(s -> s.getStatus() == OrderStatusE.FILLED));
            }

            @Test
            @DisplayName("Process Partial Fill Scenario")
            void testProcessPartialFill() {
                String eventId1 = "event-1";
                String eventId2 = "event-2";
                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 150, 150.00);
                OrderPlacedEvent sellEvent = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process(eventId1, buyEvent);
                matchingEngineService.process(eventId2, sellEvent);
                verify(execRepo, times(1)).save(any());
                verify(statusRepo, times(2)).save(orderStatusCaptor.capture());
                List<OrderStatus> statuses = orderStatusCaptor.getAllValues();

                // One PARTIALLY_FILLED, one FILLED
                long partiallyFilledCount = statuses.stream()
                        .filter(s -> s.getStatus() == OrderStatusE.PARTIALLY_FILLED)
                        .count();
                long filledCount = statuses.stream()
                        .filter(s -> s.getStatus() == OrderStatusE.FILLED)
                        .count();

                assertEquals(1, partiallyFilledCount);
                assertEquals(1, filledCount);
            }

            @Test
            @DisplayName("Process Market Order Against Limit Order")
            void testProcessMarketOrderAgainstLimit() {
                String eventId1 = "event-1";
                String eventId2 = "event-2";
                OrderPlacedEvent limitSell = createOrderPlacedEvent("1", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent marketBuy = createOrderPlacedEvent("2", "AAPL", OrderSide.BUY,
                        OrderType.MARKET, 100, null);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process(eventId1, limitSell);
                matchingEngineService.process(eventId2, marketBuy);
                verify(execRepo, times(1)).save(executionCaptor.capture());
                Execution execution = executionCaptor.getValue();
                assertEquals(new BigDecimal("150.00").setScale(2), execution.getPrice().setScale(2)); // Market takes limit price
            }
        }

        @Nested
        @DisplayName("Multiple Symbols Tests")
        class MultipleSymbolsTests {

            @Test
            @DisplayName("Process Orders for Different Symbols - Separate Order Books")
            void testProcessOrdersDifferentSymbols() {
                OrderPlacedEvent appleBuy = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent googleBuy = createOrderPlacedEvent("2", "GOOGL", OrderSide.BUY,
                        OrderType.LIMIT, 50, 2800.00);
                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process("event-1", appleBuy);
                matchingEngineService.process("event-2", googleBuy);
                // Then - Both should be processed without interference
                verify(eventsRepo, times(2)).save(any());
                verify(execRepo, never()).save(any()); // No executions, different symbols
            }

            @Test
            @DisplayName("Process Matching Orders for Same Symbol")
            void testProcessMatchingOrdersSameSymbol() {
                OrderPlacedEvent buy1 = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent sell1 = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent buy2 = createOrderPlacedEvent("3", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 50, 151.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process("event-1", buy1);
                matchingEngineService.process("event-2", sell1);
                matchingEngineService.process("event-3", buy2);

                verify(execRepo, times(1)).save(any()); // Only one execution (buy1 x sell1)
                verify(eventsRepo, times(3)).save(any()); // All events processed
            }
        }

        @Nested
        @DisplayName("Order Status Determination Tests")
        class OrderStatusTests {

            @Test
            @DisplayName("Determine PENDING Status - No Fill")
            void testDeterminePendingStatus() {
                Order order = new Order();
                order.setQuantity(100);
                order.setFilledQuantity(0);
                OrderStatusE status = matchingEngineService.determineOrderStatus(order);
                assertEquals(OrderStatusE.PENDING, status);
            }

            @Test
            @DisplayName("Determine PARTIALLY_FILLED Status")
            void testDeterminePartiallyFilledStatus() {
                Order order = new Order();
                order.setQuantity(100);
                order.setFilledQuantity(50);
                OrderStatusE status = matchingEngineService.determineOrderStatus(order);
                assertEquals(OrderStatusE.PARTIALLY_FILLED, status);
            }

            @Test
            @DisplayName("Determine FILLED Status")
            void testDetermineFilledStatus() {
                Order order = new Order();
                order.setQuantity(100);
                order.setFilledQuantity(100);
                OrderStatusE status = matchingEngineService.determineOrderStatus(order);
                assertEquals(OrderStatusE.FILLED, status);
            }
        }

        @Nested
        @DisplayName("Kafka Event Publishing Tests")
        class KafkaPublishingTests {

            @Test
            @DisplayName("Verify Kafka Events Published on Trade")
            void testKafkaEventsPublishedOnTrade() {
                String eventId1 = "event-1";
                String eventId2 = "event-2";

                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent sellEvent = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);

                matchingEngineService.process(eventId1, buyEvent);
                matchingEngineService.process(eventId2, sellEvent);
                verify(kafkaProducerService, times(2)).publishOrderExecution(any(Order.class), any(Execution.class));
            }

            @Test
            @DisplayName("No Kafka Events Published When No Trade")
            void testNoKafkaEventsWhenNoTrade() {
                String eventId = "event-1";
                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(eventId)).thenReturn(false);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process(eventId, buyEvent);
                verify(kafkaProducerService, never()).publishOrderExecution(any(), any());
            }
        }

        @Nested
        @DisplayName("Database Persistence Tests")
        class DatabasePersistenceTests {

            @Test
            @DisplayName("Verify Execution Saved to Database")
            void testExecutionSavedToDatabase() {
                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent sellEvent = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);
                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process("event-1", buyEvent);
                matchingEngineService.process("event-2", sellEvent);
                verify(execRepo, times(1)).save(executionCaptor.capture());

                Execution savedExecution = executionCaptor.getValue();
                assertNotNull(savedExecution);
                assertEquals(new BigDecimal("150.00").setScale(2), savedExecution.getPrice().setScale(2));
                assertEquals(new BigDecimal("100"), savedExecution.getQuantity());
            }

            @Test
            @DisplayName("Verify Order Status Saved to Database")
            void testOrderStatusSavedToDatabase() {
                // Given
                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent sellEvent = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process("event-1", buyEvent);
                matchingEngineService.process("event-2", sellEvent);

                verify(statusRepo, times(2)).save(orderStatusCaptor.capture());

                List<OrderStatus> savedStatuses = orderStatusCaptor.getAllValues();
                assertEquals(2, savedStatuses.size());

                for (OrderStatus status : savedStatuses) {
                    assertNotNull(status.getOrderId());
                    assertNotNull(status.getStatus());
                    assertNotNull(status.getFilledQuantity());
                    assertNotNull(status.getUpdatedAt());
                }
            }

            @Test
            @DisplayName("Verify Processed Event Saved to Database")
            void testProcessedEventSavedToDatabase() {
                String eventId = "event-123";
                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(eventId)).thenReturn(false);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process(eventId, buyEvent);
                verify(eventsRepo, times(1)).save(processedEventCaptor.capture());

                ProcessedEvent savedEvent = processedEventCaptor.getValue();
                assertEquals(eventId, savedEvent.getEventId());
                assertNotNull(savedEvent.getProcessedAt());
            }
        }

        @Nested
        @DisplayName("Error Handling Tests")
        class ErrorHandlingTests {

            @Test
            @DisplayName("Handle Null Event Gracefully")
            void testHandleNullEvent() {
                assertThrows(NullPointerException.class, () -> {
                    matchingEngineService.process("event-1", null);
                });
            }

            @Test
            @DisplayName("Handle Database Save Failure")
            void testHandleDatabaseSaveFailure() {
                OrderPlacedEvent buyEvent = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent sellEvent = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenThrow(new RuntimeException("Database error"));
                matchingEngineService.process("event-1", buyEvent);
                assertThrows(RuntimeException.class, () -> {
                    matchingEngineService.process("event-2", sellEvent);
                });
            }
        }

        @Nested
        @DisplayName("Complex Scenario Tests")
        class ComplexScenarioTests {

            @Test
            @DisplayName("Process Multiple Orders with Multiple Partial Fills")
            void testMultiplePartialFills() {
                OrderPlacedEvent largeBuy = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 300, 150.00);
                OrderPlacedEvent smallSell1 = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent smallSell2 = createOrderPlacedEvent("3", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process("event-1", largeBuy);
                matchingEngineService.process("event-2", smallSell1);
                matchingEngineService.process("event-3", smallSell2);
                verify(execRepo, times(2)).save(any()); // Two executions
                verify(statusRepo, atLeast(4)).save(any()); // Multiple status updates
            }

            @Test
            @DisplayName("Process Stop Orders")
            void testProcessStopOrders() {
                OrderPlacedEvent limitBuy = createOrderPlacedEvent("1", "AAPL", OrderSide.BUY,
                        OrderType.LIMIT, 100, 150.00);
                OrderPlacedEvent limitSell = createOrderPlacedEvent("2", "AAPL", OrderSide.SELL,
                        OrderType.LIMIT, 100, 150.00);

                OrderPlacedEvent stopOrder = createOrderPlacedEvent("3", "AAPL", OrderSide.BUY,
                        OrderType.STOP_MARKET, 50, null);
                stopOrder.setStopPrice(155.00);

                when(eventsRepo.existsById(anyString())).thenReturn(false);
                when(execRepo.save(any())).thenReturn(null);
                when(statusRepo.save(any())).thenReturn(null);
                when(eventsRepo.save(any())).thenReturn(null);
                matchingEngineService.process("event-1", limitBuy);
                matchingEngineService.process("event-2", limitSell);
                matchingEngineService.process("event-3", stopOrder);

                // Then - Stop order added but not triggered yet
                verify(execRepo, times(1)).save(any()); // Only limit orders matched
            }
        }
    }
}