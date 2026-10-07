package io.kestra.repository.h2;

import io.kestra.core.repositories.RepositoryBean;
import io.kestra.core.test.TestSuiteRunEntity;
import io.kestra.jdbc.repository.AbstractJdbcTestSuiteRunRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2TestSuiteRunRepository extends AbstractJdbcTestSuiteRunRepository {
    @Inject
    public H2TestSuiteRunRepository(@Named("testsuiteruns") H2Repository<TestSuiteRunEntity> repository) {
        super(repository);
    }
}
