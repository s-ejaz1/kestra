package io.kestra.repository.mysql;

import io.kestra.core.models.iam.IamUser;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamUserRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlIamUserRepository extends AbstractJdbcIamUserRepository {
    @Inject
    public MysqlIamUserRepository(@Named("iamUsers") MysqlRepository<IamUser> repository) {
        super(repository);
    }
}
