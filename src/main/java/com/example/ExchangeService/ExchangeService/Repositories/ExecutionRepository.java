package com.example.ExchangeService.ExchangeService.Repositories;

import com.example.ExchangeService.ExchangeService.entities.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, Long> {}