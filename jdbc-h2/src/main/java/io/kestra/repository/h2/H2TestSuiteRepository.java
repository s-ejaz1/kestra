package io.kestra.repository.h2;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.test.TestSuite;
import io.kestra.jdbc.repository.AbstractJdbcTestSuiteRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2TestSuiteRepository extends AbstractJdbcTestSuiteRepository {
    @Inject
    public H2TestSuiteRepository(@Named("testsuites") H2Repository<TestSuite> repository) {
        super(repository);
    }
}
