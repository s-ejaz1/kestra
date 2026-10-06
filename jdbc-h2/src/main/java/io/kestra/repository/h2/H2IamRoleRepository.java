package io.kestra.repository.h2;

import io.kestra.core.models.iam.IamRole;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamRoleRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2IamRoleRepository extends AbstractJdbcIamRoleRepository {
    @Inject
    public H2IamRoleRepository(@Named("iamRoles") H2Repository<IamRole> repository) {
        super(repository);
    }
}
