package io.kestra.repository.mysql;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.test.TestSuite;
import io.kestra.jdbc.repository.AbstractJdbcTestSuiteRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlTestSuiteRepository extends AbstractJdbcTestSuiteRepository {
    @Inject
    public MysqlTestSuiteRepository(@Named("testsuites") MysqlRepository<TestSuite> repository) {
        super(repository);
    }
}
