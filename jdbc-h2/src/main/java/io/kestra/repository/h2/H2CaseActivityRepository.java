package io.kestra.repository.h2;

import io.kestra.core.models.cases.CaseActivity;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCaseActivityRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2CaseActivityRepository extends AbstractJdbcCaseActivityRepository {
    @Inject
    public H2CaseActivityRepository(@Named("caseactivities") H2Repository<CaseActivity> repository) {
        super(repository);
    }
}
