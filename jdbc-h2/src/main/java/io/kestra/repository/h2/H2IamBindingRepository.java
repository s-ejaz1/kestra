package io.kestra.repository.h2;

import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcIamBindingRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2IamBindingRepository extends AbstractJdbcIamBindingRepository {
    @Inject
    public H2IamBindingRepository(@Named("iamBindings") H2Repository<IamBinding> repository) {
        super(repository);
    }
}
