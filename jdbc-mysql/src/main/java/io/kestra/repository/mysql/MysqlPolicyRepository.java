package io.kestra.repository.mysql;

import io.kestra.core.models.policies.Policy;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcPolicyRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlPolicyRepository extends AbstractJdbcPolicyRepository {
    @Inject
    public MysqlPolicyRepository(@Named("policies") MysqlRepository<Policy> repository) {
        super(repository);
    }
}
