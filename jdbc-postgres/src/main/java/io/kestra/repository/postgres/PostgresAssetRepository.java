package io.kestra.repository.postgres;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresAssetRepository extends AbstractJdbcAssetRepository {
    @Inject
    public PostgresAssetRepository(@Named("assets") PostgresRepository<Asset> repository) {
        super(repository);
    }
}
