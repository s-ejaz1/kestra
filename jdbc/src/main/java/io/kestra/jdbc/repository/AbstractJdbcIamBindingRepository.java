package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.impl.DSL;

import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.repositories.IamBindingRepositoryInterface;

public abstract class AbstractJdbcIamBindingRepository extends AbstractJdbcCrudRepository<IamBinding> implements IamBindingRepositoryInterface {

    protected AbstractJdbcIamBindingRepository(io.kestra.jdbc.AbstractJdbcRepository<IamBinding> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<IamBinding> findById(String tenantId, String id) {
        return findOne(tenantId, field("id").eq(id));
    }

    @Override
    public List<IamBinding> findByUserId(String tenantId, String userId) {
        return find(tenantId, field("user_id").eq(userId));
    }

    @Override
    public List<IamBinding> findByRoleId(String tenantId, String roleId) {
        return find(tenantId, field("role_id").eq(roleId));
    }

    @Override
    public List<IamBinding> findAll(String tenantId) {
        return find(tenantId, DSL.noCondition());
    }

    @Override
    public IamBinding save(IamBinding binding) {
        return super.save(binding.getCreated() == null ? binding.toBuilder().created(Instant.now()).build() : binding);
    }
}
