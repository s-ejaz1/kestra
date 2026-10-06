package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.secret.PersistedSecretMetadata;
import io.kestra.core.repositories.SecretMetadataRepositoryInterface;

public abstract class AbstractJdbcSecretMetadataRepository extends AbstractJdbcCrudRepository<PersistedSecretMetadata> implements SecretMetadataRepositoryInterface {

    protected AbstractJdbcSecretMetadataRepository(io.kestra.jdbc.AbstractJdbcRepository<PersistedSecretMetadata> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    protected Condition defaultFilter(String tenantId, boolean allowDeleted) {
        return super.defaultFilter(tenantId, allowDeleted).and(aclCondition(QueryFilter.Resource.SECRET_METADATA));
    }

    @Override
    protected Condition findQueryCondition(String query) {
        return field("name").likeIgnoreCase("%" + query + "%");
    }

    @Override
    public Optional<PersistedSecretMetadata> findByName(String tenantId, String namespace, String name) {
        return findOne(tenantId, field("namespace").eq(namespace).and(field("name").eq(name)));
    }

    @Override
    public List<PersistedSecretMetadata> find(String tenantId, List<QueryFilter> filters) {
        return find(tenantId, this.filter(filters, "updated", QueryFilter.Resource.SECRET_METADATA), field("name").asc());
    }

    @Override
    public PersistedSecretMetadata save(PersistedSecretMetadata item) {
        Instant now = Instant.now();
        PersistedSecretMetadata toPersist = item.toBuilder()
            .created(findByName(item.getTenantId(), item.getNamespace(), item.getName())
                .map(PersistedSecretMetadata::getCreated)
                .orElse(now))
            .updated(now)
            .build();
        return super.save(toPersist);
    }
}
