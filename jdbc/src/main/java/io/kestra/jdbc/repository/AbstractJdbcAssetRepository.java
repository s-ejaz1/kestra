package io.kestra.jdbc.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.impl.DSL;

import io.kestra.core.models.AccessScope;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.AssetRepositoryInterface;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.annotation.Nullable;

public abstract class AbstractJdbcAssetRepository extends AbstractJdbcCrudRepository<Asset> implements AssetRepositoryInterface {

    protected AbstractJdbcAssetRepository(io.kestra.jdbc.AbstractJdbcRepository<Asset> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<Asset> findById(String tenantId, String id) {
        return findOne(tenantId, field("id").eq(id));
    }

    @Override
    public ArrayListTotal<Asset> find(Pageable pageable, String tenantId, AccessScope scope, @Nullable String query, @Nullable String namespace, @Nullable String type) {
        Condition condition = scopeCondition(scope);
        if (query != null && !query.isBlank()) {
            String lowered = query.toLowerCase();
            condition = condition.and(
                DSL.lower(field("id", String.class)).contains(lowered).or(DSL.lower(field("display_name", String.class)).contains(lowered))
            );
        }
        if (namespace != null) {
            condition = condition.and(field("namespace", String.class).eq(namespace).or(field("namespace", String.class).startsWith(namespace + ".")));
        }
        if (type != null) {
            condition = condition.and(field("type").eq(type));
        }
        return findPage(Pageable.from(pageable.getNumber(), pageable.getSize(), Sort.of(Sort.Order.asc("id"))), tenantId, condition);
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
