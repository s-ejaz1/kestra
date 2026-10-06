package io.kestra.repository.postgres;

import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamBindingRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresIamBindingRepository extends AbstractJdbcIamBindingRepository {
    @Inject
    public PostgresIamBindingRepository(@Named("iamBindings") PostgresRepository<IamBinding> repository) {
        super(repository);
    }
}
