package io.kestra.repository.postgres;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.test.TestSuite;
import io.kestra.jdbc.repository.AbstractJdbcTestSuiteRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresTestSuiteRepository extends AbstractJdbcTestSuiteRepository {
    @Inject
    public PostgresTestSuiteRepository(@Named("testsuites") PostgresRepository<TestSuite> repository) {
        super(repository);
    }
}
