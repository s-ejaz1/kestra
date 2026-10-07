package io.kestra.repository.h2;

import io.kestra.core.models.assets.AssetLineageEdge;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAssetLineageRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AssetLineageRepository extends AbstractJdbcAssetLineageRepository {
    @Inject
    public H2AssetLineageRepository(@Named("assetlineage") H2Repository<AssetLineageEdge> repository) {
        super(repository);
    }
}
