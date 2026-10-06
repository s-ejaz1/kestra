package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.impl.DSL;

import io.kestra.core.models.iam.IamRole;
import io.kestra.core.repositories.IamRoleRepositoryInterface;

public abstract class AbstractJdbcIamRoleRepository extends AbstractJdbcCrudRepository<IamRole> implements IamRoleRepositoryInterface {

    protected AbstractJdbcIamRoleRepository(io.kestra.jdbc.AbstractJdbcRepository<IamRole> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<IamRole> findById(String tenantId, String id) {
        return findOne(tenantId, field("id").eq(id));
    }

    @Override
    public List<IamRole> findAll(String tenantId) {
        return find(tenantId, DSL.noCondition(), field("name").asc());
    }

    @Override
    public IamRole save(IamRole role) {
        Instant now = Instant.now();
        return super.save(role.toBuilder().created(role.getCreated() == null ? now : role.getCreated()).updated(now).build());
    }
}
