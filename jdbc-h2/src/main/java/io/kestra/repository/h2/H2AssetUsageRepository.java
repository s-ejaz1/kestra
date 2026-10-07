package io.kestra.repository.h2;

import io.kestra.core.models.assets.AssetUsage;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetUsageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AssetUsageRepository extends AbstractJdbcAssetUsageRepository {
    @Inject
    public H2AssetUsageRepository(@Named("assetusages") H2Repository<AssetUsage> repository) {
        super(repository);
    }
}
