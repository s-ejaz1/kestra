package io.kestra.core.repositories;

import io.kestra.core.models.assets.AssetUsage;

import io.micronaut.data.model.Pageable;

public interface AssetUsageRepositoryInterface {
    AssetUsage save(AssetUsage usage);

    ArrayListTotal<AssetUsage> find(Pageable pageable, String tenantId, String assetId);
}
