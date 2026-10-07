package io.kestra.repository.postgres;

import io.kestra.core.models.policies.Policy;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcPolicyRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresPolicyRepository extends AbstractJdbcPolicyRepository {
    @Inject
    public PostgresPolicyRepository(@Named("policies") PostgresRepository<Policy> repository) {
        super(repository);
    }
}
