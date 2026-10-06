package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.secret.PersistedSecretMetadata;

public interface SecretMetadataRepositoryInterface {
    Optional<PersistedSecretMetadata> findByName(String tenantId, String namespace, String name);

    List<PersistedSecretMetadata> find(String tenantId, List<QueryFilter> filters);

    PersistedSecretMetadata save(PersistedSecretMetadata item);

    default PersistedSecretMetadata delete(PersistedSecretMetadata item) {
        return this.save(item.toDeleted());
    }
}
