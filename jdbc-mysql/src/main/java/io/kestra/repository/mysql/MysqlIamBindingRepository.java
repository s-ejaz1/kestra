package io.kestra.repository.mysql;

import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamBindingRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlIamBindingRepository extends AbstractJdbcIamBindingRepository {
    @Inject
    public MysqlIamBindingRepository(@Named("iamBindings") MysqlRepository<IamBinding> repository) {
        super(repository);
    }
}
