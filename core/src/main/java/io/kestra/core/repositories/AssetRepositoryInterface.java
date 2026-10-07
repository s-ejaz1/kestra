package io.kestra.core.repositories;

import java.util.Optional;

import io.kestra.core.models.AccessScope;
import io.kestra.core.models.assets.Asset;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

public interface AssetRepositoryInterface {
    Optional<Asset> findById(String tenantId, String id);

    /**
     * Assets without a namespace are only visible to a {@link AccessScope.Kind#GLOBAL} scope.
     */
    ArrayListTotal<Asset> find(Pageable pageable, String tenantId, AccessScope scope, @Nullable String query, @Nullable String namespace, @Nullable String type);

    Asset save(Asset asset);
}
