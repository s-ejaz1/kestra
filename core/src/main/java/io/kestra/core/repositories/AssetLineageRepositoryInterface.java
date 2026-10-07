package io.kestra.core.repositories;

import java.util.List;

import io.kestra.core.models.assets.AssetLineageEdge;

public interface AssetLineageRepositoryInterface {
    AssetLineageEdge save(AssetLineageEdge edge);

    List<AssetLineageEdge> findBySource(String tenantId, String sourceId);

    List<AssetLineageEdge> findByTarget(String tenantId, String targetId);
}
