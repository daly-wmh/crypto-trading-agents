package com.tradingagents.api.repository;

import com.tradingagents.api.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {
    List<ModelProvider> findByTypeOrderByCreatedAt(String type);
}
