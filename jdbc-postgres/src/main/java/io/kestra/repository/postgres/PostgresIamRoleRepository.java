package io.kestra.repository.postgres;

import io.kestra.core.models.iam.IamRole;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamRoleRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresIamRoleRepository extends AbstractJdbcIamRoleRepository {
    @Inject
    public PostgresIamRoleRepository(@Named("iamRoles") PostgresRepository<IamRole> repository) {
        super(repository);
    }
}
