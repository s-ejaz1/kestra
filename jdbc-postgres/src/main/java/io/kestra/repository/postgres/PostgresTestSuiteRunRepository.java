package io.kestra.repository.postgres;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.test.TestSuiteRunEntity;
import io.kestra.jdbc.repository.AbstractJdbcTestSuiteRunRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresTestSuiteRunRepository extends AbstractJdbcTestSuiteRunRepository {
    @Inject
    public PostgresTestSuiteRunRepository(@Named("testsuiteruns") PostgresRepository<TestSuiteRunEntity> repository) {
        super(repository);
    }
}
