package io.kestra.jdbc.repository;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.models.audit.AuditLog;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.AuditLogRepositoryInterface;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.annotation.Nullable;

public abstract class AbstractJdbcAuditLogRepository extends AbstractJdbcCrudRepository<AuditLog> implements AuditLogRepositoryInterface {

    protected AbstractJdbcAuditLogRepository(io.kestra.jdbc.AbstractJdbcRepository<AuditLog> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public ArrayListTotal<AuditLog> find(Pageable pageable, String tenantId, @Nullable String namespace, @Nullable String userEmail, @Nullable String resource) {
        Condition condition = DSL.noCondition();
        if (namespace != null) {
            condition = condition.and(field("namespace", String.class).eq(namespace).or(field("namespace", String.class).startsWith(namespace + ".")));
        }
        if (userEmail != null) {
            condition = condition.and(DSL.lower(field("user_email", String.class)).contains(userEmail.toLowerCase()));
        }
        if (resource != null) {
            condition = condition.and(field("resource").eq(resource));
        }
        return findPage(Pageable.from(pageable.getNumber(), pageable.getSize(), Sort.of(Sort.Order.desc("date"))), tenantId, condition);
    }
}
