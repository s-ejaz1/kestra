package io.kestra.jdbc.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.models.AccessScope;
import io.kestra.core.models.cases.Case;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.CaseRepositoryInterface;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.annotation.Nullable;

public abstract class AbstractJdbcCaseRepository extends AbstractJdbcCrudRepository<Case> implements CaseRepositoryInterface {
    private static final List<String> OPEN_STATUSES = List.of(Case.Status.OPEN.name(), Case.Status.ACKNOWLEDGED.name());

    protected AbstractJdbcCaseRepository(io.kestra.jdbc.AbstractJdbcRepository<Case> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<Case> findById(String tenantId, String id) {
        return findOne(tenantId, field("id").eq(id));
    }

    @Override
    public ArrayListTotal<Case> find(
        Pageable pageable,
        String tenantId,
        AccessScope scope,
        @Nullable String query,
        @Nullable List<Case.Status> statuses,
        @Nullable Case.Severity severity,
        @Nullable String namespace) {
        Condition condition = scopeCondition(scope);
        if (query != null && !query.isBlank()) {
            condition = condition.and(DSL.lower(field("title", String.class)).contains(query.toLowerCase()).or(field("id", String.class).eq(query)));
        }
        if (statuses != null && !statuses.isEmpty()) {
            condition = condition.and(field("status").in(statuses.stream().map(Enum::name).toList()));
        }
        if (severity != null) {
            condition = condition.and(field("severity").eq(severity.name()));
        }
        if (namespace != null) {
            condition = condition.and(field("namespace", String.class).eq(namespace).or(field("namespace", String.class).startsWith(namespace + ".")));
        }
        return findPage(Pageable.from(pageable.getNumber(), pageable.getSize(), Sort.of(Sort.Order.desc("updated"))), tenantId, condition);
    }

    @Override
    public List<Case> findOpenAutoLinked(String tenantId, String namespace, String flowId) {
        return find(
            tenantId,
            field("namespace").eq(namespace).and(field("flow_id").eq(flowId)).and(field("auto_link").isTrue()).and(field("status").in(OPEN_STATUSES))
        );
    }

    private static Condition scopeCondition(AccessScope scope) {
        return switch (scope.kind()) {
            case GLOBAL -> DSL.noCondition();
            case DENY_ALL -> DSL.falseCondition();
            case NAMESPACES -> {
                List<Condition> ors = new ArrayList<>(scope.namespaces().size() * 2);
                for (String namespace : scope.namespaces()) {
                    ors.add(field("namespace", String.class).eq(namespace));
                    ors.add(field("namespace", String.class).startsWith(namespace + "."));
                }
                yield DSL.or(ors);
            }
        };
    }
}
