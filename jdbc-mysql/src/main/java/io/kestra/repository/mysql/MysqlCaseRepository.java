package io.kestra.repository.mysql;

import io.kestra.core.models.cases.Case;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCaseRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlCaseRepository extends AbstractJdbcCaseRepository {
    @Inject
    public MysqlCaseRepository(@Named("cases") MysqlRepository<Case> repository) {
        super(repository);
    }
}
