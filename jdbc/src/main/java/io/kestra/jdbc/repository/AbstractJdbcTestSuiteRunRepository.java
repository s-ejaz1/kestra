package io.kestra.jdbc.repository;

import java.util.Optional;

import io.kestra.core.repositories.TestSuiteRunRepositoryInterface;
import io.kestra.core.test.TestSuiteRunEntity;

public abstract class AbstractJdbcTestSuiteRunRepository extends AbstractJdbcCrudRepository<TestSuiteRunEntity> implements TestSuiteRunRepositoryInterface {

    protected AbstractJdbcTestSuiteRunRepository(io.kestra.jdbc.AbstractJdbcRepository<TestSuiteRunEntity> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<TestSuiteRunEntity> findLatest(String tenantId, String namespace, String testSuiteId) {
        return findOne(tenantId, field("namespace").eq(namespace).and(field("test_suite_id").eq(testSuiteId)), field("start_date").desc());
    }
}
