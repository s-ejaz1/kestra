package io.kestra.jdbc.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jooq.impl.DSL;

import io.kestra.core.models.apps.App;
import io.kestra.core.repositories.AppRepositoryInterface;

public abstract class AbstractJdbcAppRepository extends AbstractJdbcCrudRepository<App> implements AppRepositoryInterface {

    protected AbstractJdbcAppRepository(io.kestra.jdbc.AbstractJdbcRepository<App> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<App> findById(String tenantId, String namespace, String id) {
        return findOne(tenantId, field("namespace").eq(namespace).and(field("id").eq(id)));
    }

    @Override
    public List<App> findAll(String tenantId) {
        return find(tenantId, DSL.noCondition(), field("namespace").asc(), field("id").asc());
    }

    @Override
    public App save(App app) {
        Instant now = Instant.now();
        return super.save(app.toBuilder().created(app.getCreated() == null ? now : app.getCreated()).updated(now).build());
    }
}
