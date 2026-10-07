package io.kestra.repository.mysql;

import io.kestra.core.models.assets.Asset;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAssetRepository extends AbstractJdbcAssetRepository {
    @Inject
    public MysqlAssetRepository(@Named("assets") MysqlRepository<Asset> repository) {
        super(repository);
    }
}
