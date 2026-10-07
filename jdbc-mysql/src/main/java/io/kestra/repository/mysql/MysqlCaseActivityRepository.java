package io.kestra.repository.mysql;

import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCaseActivityRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlCaseActivityRepository extends AbstractJdbcCaseActivityRepository {
    @Inject
    public MysqlCaseActivityRepository(@Named("caseactivities") MysqlRepository<CaseActivity> repository) {
        super(repository);
    }
}
