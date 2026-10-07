package io.kestra.jdbc.repository;

import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.CaseActivityRepositoryInterface;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;

public abstract class AbstractJdbcCaseActivityRepository extends AbstractJdbcCrudRepository<CaseActivity> implements CaseActivityRepositoryInterface {

    protected AbstractJdbcCaseActivityRepository(io.kestra.jdbc.AbstractJdbcRepository<CaseActivity> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public ArrayListTotal<CaseActivity> find(Pageable pageable, String tenantId, String caseId) {
        return findPage(Pageable.from(pageable.getNumber(), pageable.getSize(), Sort.of(Sort.Order.asc("date"))), tenantId, field("case_id").eq(caseId));
    }
}
