package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.impl.DSL;

import io.kestra.core.models.policies.Policy;
import io.kestra.core.repositories.PolicyRepositoryInterface;

public abstract class AbstractJdbcPolicyRepository extends AbstractJdbcCrudRepository<Policy> implements PolicyRepositoryInterface {

    protected AbstractJdbcPolicyRepository(io.kestra.jdbc.AbstractJdbcRepository<Policy> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<Policy> findById(String tenantId, String id) {
        return findOne(tenantId, field("id").eq(id));
    }

    @Override
    public List<Policy> findAll(String tenantId) {
        return find(tenantId, DSL.noCondition(), field("id").asc());
    }

    @Override
    public Policy save(Policy policy) {
        Instant now = Instant.now();
        return super.save(policy.toBuilder().created(policy.getCreated() == null ? now : policy.getCreated()).updated(now).build());
    }
}
