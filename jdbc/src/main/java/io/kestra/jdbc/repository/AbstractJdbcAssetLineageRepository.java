package io.kestra.jdbc.repository;

import java.util.List;

import io.kestra.core.models.assets.AssetLineageEdge;
import io.kestra.core.repositories.AssetLineageRepositoryInterface;

public abstract class AbstractJdbcAssetLineageRepository extends AbstractJdbcCrudRepository<AssetLineageEdge> implements AssetLineageRepositoryInterface {

    protected AbstractJdbcAssetLineageRepository(io.kestra.jdbc.AbstractJdbcRepository<AssetLineageEdge> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public List<AssetLineageEdge> findBySource(String tenantId, String sourceId) {
        return find(tenantId, field("source_id").eq(sourceId));
    }

    @Override
    public List<AssetLineageEdge> findByTarget(String tenantId, String targetId) {
        return find(tenantId, field("target_id").eq(targetId));
    }
}
