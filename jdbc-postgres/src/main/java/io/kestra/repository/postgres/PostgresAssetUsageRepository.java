package io.kestra.repository.postgres;

import io.kestra.core.models.assets.AssetUsage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetUsageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresAssetUsageRepository extends AbstractJdbcAssetUsageRepository {
    @Inject
    public PostgresAssetUsageRepository(@Named("assetusages") PostgresRepository<AssetUsage> repository) {
        super(repository);
    }
}
