package io.kestra.repository.postgres;

import io.kestra.core.models.secret.PersistedSecretMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcSecretMetadataRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresSecretMetadataRepository extends AbstractJdbcSecretMetadataRepository {
    @Inject
    public PostgresSecretMetadataRepository(@Named("secretMetadata") PostgresRepository<PersistedSecretMetadata> repository) {
        super(repository);
    }
}
