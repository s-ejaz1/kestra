package io.kestra.jdbc.repository;

import io.kestra.core.models.assets.AssetUsage;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.AssetUsageRepositoryInterface;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

public abstract class AbstractJdbcAssetUsageRepository extends AbstractJdbcCrudRepository<AssetUsage> implements AssetUsageRepositoryInterface {

    protected AbstractJdbcAssetUsageRepository(io.kestra.jdbc.AbstractJdbcRepository<AssetUsage> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public ArrayListTotal<AssetUsage> find(Pageable pageable, String tenantId, String assetId) {
        return findPage(Pageable.from(pageable.getNumber(), pageable.getSize(), Sort.of(Sort.Order.desc("date"))), tenantId, field("asset_id").eq(assetId));
    }
}
