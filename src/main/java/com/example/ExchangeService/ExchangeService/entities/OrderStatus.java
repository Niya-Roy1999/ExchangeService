package com.example.ExchangeService.ExchangeService.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// domain/OrderStatus.java
@Entity
@Table(name = "order_status")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatus {
    @Id
    private Long orderId;
    private String status;           // e.g. FILLED
    private BigDecimal filledQuantity;
    private LocalDateTime updatedAt;
}