package io.kestra.jdbc.repository;

import java.util.List;
import java.util.Optional;

import org.jooq.impl.DSL;

import io.kestra.core.repositories.TestSuiteRepositoryInterface;
import io.kestra.core.test.TestSuite;

public abstract class AbstractJdbcTestSuiteRepository extends AbstractJdbcCrudRepository<TestSuite> implements TestSuiteRepositoryInterface {

    protected AbstractJdbcTestSuiteRepository(io.kestra.jdbc.AbstractJdbcRepository<TestSuite> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    public Optional<TestSuite> findById(String tenantId, String namespace, String id) {
        return findOne(tenantId, field("namespace").eq(namespace).and(field("id").eq(id)));
    }

    @Override
    public List<TestSuite> findAll(String tenantId) {
        return find(tenantId, DSL.noCondition(), field("namespace").asc(), field("id").asc());
    }

    @Override
    public TestSuite delete(TestSuite testSuite) {
        return super.save(testSuite.toDeleted());
    }
}
