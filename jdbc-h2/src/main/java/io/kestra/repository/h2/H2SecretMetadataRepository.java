package io.kestra.repository.h2;

import io.kestra.core.models.secret.PersistedSecretMetadata;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcSecretMetadataRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2SecretMetadataRepository extends AbstractJdbcSecretMetadataRepository {
    @Inject
    public H2SecretMetadataRepository(@Named("secretMetadata") H2Repository<PersistedSecretMetadata> repository) {
        super(repository);
    }
}
