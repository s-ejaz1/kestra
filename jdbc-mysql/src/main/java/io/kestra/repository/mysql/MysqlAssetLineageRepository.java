package io.kestra.repository.mysql;

import io.kestra.core.models.assets.AssetLineageEdge;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetLineageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAssetLineageRepository extends AbstractJdbcAssetLineageRepository {
    @Inject
    public MysqlAssetLineageRepository(@Named("assetlineage") MysqlRepository<AssetLineageEdge> repository) {
        super(repository);
    }
}
