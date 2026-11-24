package com.tradingagents.trading.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trading_account")
public class TradingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String name;

    @Column(precision = 28, scale = 8)
    private BigDecimal balance;

    @Column(precision = 28, scale = 8)
    private BigDecimal frozen;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (balance == null) balance = new BigDecimal("100000.00");
        if (frozen == null) frozen = BigDecimal.ZERO;
    }

    public BigDecimal getAvailable() {
        return balance.subtract(frozen);
    }
}
