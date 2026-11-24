package com.tradingagents.trading.repository;

import com.tradingagents.trading.entity.TradePosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradePositionRepository extends JpaRepository<TradePosition, Long> {
    List<TradePosition> findByAccountId(Long accountId);
    Optional<TradePosition> findByAccountIdAndStockCode(Long accountId, String stockCode);
}
