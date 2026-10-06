package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.impl.DSL;

import io.kestra.core.models.iam.IamUser;
import io.kestra.core.repositories.IamUserRepositoryInterface;

public abstract class AbstractJdbcIamUserRepository extends AbstractJdbcCrudRepository<IamUser> implements IamUserRepositoryInterface {

    protected AbstractJdbcIamUserRepository(io.kestra.jdbc.AbstractJdbcRepository<IamUser> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<IamUser> findById(String tenantId, String id) {
        return findOne(tenantId, field("id").eq(id));
    }

    @Override
    public Optional<IamUser> findByEmail(String tenantId, String email) {
        return findOne(tenantId, DSL.lower(field("email", String.class)).eq(email.toLowerCase()));
    }

    @Override
    public List<IamUser> findAll(String tenantId) {
        return find(tenantId, DSL.noCondition(), field("email").asc());
    }

    @Override
    public IamUser save(IamUser user) {
        Instant now = Instant.now();
        return super.save(user.toBuilder().created(user.getCreated() == null ? now : user.getCreated()).updated(now).build());
    }
}
