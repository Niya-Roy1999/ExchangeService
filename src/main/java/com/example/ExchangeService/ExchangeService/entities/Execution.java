package com.example.ExchangeService.ExchangeService.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;
    private Long counterOrderId;
    private String userId;
    private String instrumentSymbol;
    private String side;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal notional;
    private LocalDateTime executedAt;
}