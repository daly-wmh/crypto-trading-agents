package com.tradingagents.trading.repository;

import com.tradingagents.trading.entity.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
    List<TradeOrder> findByAccountIdOrderByCreatedAtDesc(Long accountId);
    Optional<TradeOrder> findByAccountIdAndAnalysisId(Long accountId, Long analysisId);
}
