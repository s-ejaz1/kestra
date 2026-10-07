package io.kestra.repository.h2;

import io.kestra.core.models.cases.Case;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCaseRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2CaseRepository extends AbstractJdbcCaseRepository {
    @Inject
    public H2CaseRepository(@Named("cases") H2Repository<Case> repository) {
        super(repository);
    }
}
