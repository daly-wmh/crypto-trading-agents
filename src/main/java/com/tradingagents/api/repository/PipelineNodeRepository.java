package com.tradingagents.api.repository;

import com.tradingagents.api.entity.PipelineNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineNodeRepository extends JpaRepository<PipelineNode, Long> {

    List<PipelineNode> findAllByOrderByStageOrderAscPositionInStageAsc();

    List<PipelineNode> findByEnabledTrueOrderByStageOrderAscPositionInStageAsc();

    Optional<PipelineNode> findByNodeKey(String nodeKey);

    boolean existsByNodeKey(String nodeKey);
}
