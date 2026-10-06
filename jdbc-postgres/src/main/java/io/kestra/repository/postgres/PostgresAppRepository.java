package io.kestra.repository.postgres;

import io.kestra.core.models.apps.App;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAppRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresAppRepository extends AbstractJdbcAppRepository {
    @Inject
    public PostgresAppRepository(@Named("apps") PostgresRepository<App> repository) {
        super(repository);
    }
}
