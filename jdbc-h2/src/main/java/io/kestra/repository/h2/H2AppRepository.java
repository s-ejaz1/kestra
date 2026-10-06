package io.kestra.repository.h2;

import io.kestra.core.models.apps.App;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAppRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AppRepository extends AbstractJdbcAppRepository {
    @Inject
    public H2AppRepository(@Named("apps") H2Repository<App> repository) {
        super(repository);
    }
}
