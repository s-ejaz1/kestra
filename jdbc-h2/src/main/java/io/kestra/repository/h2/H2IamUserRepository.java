package io.kestra.repository.h2;

import io.kestra.core.models.iam.IamUser;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamUserRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2IamUserRepository extends AbstractJdbcIamUserRepository {
    @Inject
    public H2IamUserRepository(@Named("iamUsers") H2Repository<IamUser> repository) {
        super(repository);
    }
}
