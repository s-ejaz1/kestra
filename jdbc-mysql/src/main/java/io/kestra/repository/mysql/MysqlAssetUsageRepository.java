package io.kestra.repository.mysql;

import io.kestra.core.models.assets.AssetUsage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetUsageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAssetUsageRepository extends AbstractJdbcAssetUsageRepository {
    @Inject
    public MysqlAssetUsageRepository(@Named("assetusages") MysqlRepository<AssetUsage> repository) {
        super(repository);
    }
}
