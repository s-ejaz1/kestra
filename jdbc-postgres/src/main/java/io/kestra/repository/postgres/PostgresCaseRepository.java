package io.kestra.repository.postgres;

import io.kestra.core.models.cases.Case;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcCaseRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresCaseRepository extends AbstractJdbcCaseRepository {
    @Inject
    public PostgresCaseRepository(@Named("cases") PostgresRepository<Case> repository) {
        super(repository);
    }
}
