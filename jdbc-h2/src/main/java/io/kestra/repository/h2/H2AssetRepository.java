package io.kestra.repository.h2;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AssetRepository extends AbstractJdbcAssetRepository {
    @Inject
    public H2AssetRepository(@Named("assets") H2Repository<Asset> repository) {
        super(repository);
    }
}
