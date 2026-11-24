package com.tradingagents.trading.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "trade_position", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"account_id", "stock_code"})
})
public class TradePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "stock_code", length = 16, nullable = false)
    private String stockCode;

    @Column(precision = 28, scale = 8, nullable = false)
    private BigDecimal quantity;

    @Column(name = "avg_cost", precision = 28, scale = 8)
    private BigDecimal avgCost;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() { updatedAt = LocalDateTime.now(); }

    public BigDecimal marketValue(BigDecimal currentPrice) {
        return currentPrice.multiply(quantity);
    }

    public BigDecimal unrealizedPnl(BigDecimal currentPrice) {
        return currentPrice.subtract(avgCost).multiply(quantity);
    }

    public BigDecimal pnlRatio(BigDecimal currentPrice) {
        if (avgCost.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return currentPrice.subtract(avgCost)
                .divide(avgCost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
