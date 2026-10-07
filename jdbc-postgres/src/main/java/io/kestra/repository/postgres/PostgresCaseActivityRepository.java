package io.kestra.repository.postgres;

import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCaseActivityRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresCaseActivityRepository extends AbstractJdbcCaseActivityRepository {
    @Inject
    public PostgresCaseActivityRepository(@Named("caseactivities") PostgresRepository<CaseActivity> repository) {
        super(repository);
    }
}
