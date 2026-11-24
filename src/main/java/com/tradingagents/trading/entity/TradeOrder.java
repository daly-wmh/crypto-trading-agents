package com.tradingagents.trading.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Entity
@Table(name = "trade_order")
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "stock_code", length = 16, nullable = false)
    private String stockCode;

    @Column(length = 4, nullable = false)
    private String direction;

    @Column(precision = 28, scale = 8, nullable = false)
    private BigDecimal price;

    @Column(precision = 28, scale = 8, nullable = false)
    private BigDecimal quantity;

    @Column(precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(precision = 28, scale = 8, nullable = false)
    private BigDecimal fee;

    @Column(length = 10)
    private String status;

    @Column(name = "analysis_id")
    private Long analysisId;

    @Column(length = 200)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Instant getExecutedAt() {
        return createdAt == null ? null : createdAt.atZone(ZoneId.systemDefault()).toInstant();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "FILLED";
        if (amount == null) amount = price.multiply(quantity);
    }
}
