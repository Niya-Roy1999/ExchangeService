package com.example.ExchangeService.ExchangeService.Repositories;

import com.example.ExchangeService.ExchangeService.entities.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderStatusRepository extends JpaRepository<OrderStatus, Long> {}