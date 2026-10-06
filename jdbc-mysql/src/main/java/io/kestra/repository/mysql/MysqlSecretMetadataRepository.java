package io.kestra.repository.mysql;

import io.kestra.core.models.secret.PersistedSecretMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcSecretMetadataRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlSecretMetadataRepository extends AbstractJdbcSecretMetadataRepository {
    @Inject
    public MysqlSecretMetadataRepository(@Named("secretMetadata") MysqlRepository<PersistedSecretMetadata> repository) {
        super(repository);
    }
}
