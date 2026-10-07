package io.kestra.repository.mysql;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.test.TestSuiteRunEntity;
import io.kestra.jdbc.repository.AbstractJdbcTestSuiteRunRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlTestSuiteRunRepository extends AbstractJdbcTestSuiteRunRepository {
    @Inject
    public MysqlTestSuiteRunRepository(@Named("testsuiteruns") MysqlRepository<TestSuiteRunEntity> repository) {
        super(repository);
    }
}
