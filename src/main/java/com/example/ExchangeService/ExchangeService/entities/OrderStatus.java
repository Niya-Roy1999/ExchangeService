package com.example.ExchangeService.ExchangeService.entities;

import com.example.ExchangeService.ExchangeService.enums.OrderStatusE;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatus {
    @Id
    private Long orderId;

    @Enumerated(EnumType.STRING)
    private OrderStatusE status;           // e.g. FILLED
    private BigDecimal filledQuantity;
    private Instant updatedAt;
}