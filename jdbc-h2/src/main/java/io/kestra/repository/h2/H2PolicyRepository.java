package io.kestra.repository.h2;

import io.kestra.core.models.policies.Policy;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcPolicyRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2PolicyRepository extends AbstractJdbcPolicyRepository {
    @Inject
    public H2PolicyRepository(@Named("policies") H2Repository<Policy> repository) {
        super(repository);
    }
}
