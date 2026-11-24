package com.tradingagents.api.repository;

import com.tradingagents.api.entity.AgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {
    Optional<AgentConfig> findByAgentId(String agentId);
    List<AgentConfig> findAllByOrderByExecutionOrderAsc();
}
