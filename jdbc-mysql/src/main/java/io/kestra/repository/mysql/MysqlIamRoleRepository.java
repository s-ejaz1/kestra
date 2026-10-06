package io.kestra.repository.mysql;

import io.kestra.core.models.iam.IamRole;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamRoleRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlIamRoleRepository extends AbstractJdbcIamRoleRepository {
    @Inject
    public MysqlIamRoleRepository(@Named("iamRoles") MysqlRepository<IamRole> repository) {
        super(repository);
    }
}
