package com.tradingagents.trading.repository;

import com.tradingagents.trading.entity.TradingAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from TradingAccount account where account.id = :id")
    Optional<TradingAccount> lockById(@Param("id") Long id);
}
