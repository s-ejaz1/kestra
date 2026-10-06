package io.kestra.repository.postgres;

import io.kestra.core.models.iam.IamUser;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamUserRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresIamUserRepository extends AbstractJdbcIamUserRepository {
    @Inject
    public PostgresIamUserRepository(@Named("iamUsers") PostgresRepository<IamUser> repository) {
        super(repository);
    }
}
