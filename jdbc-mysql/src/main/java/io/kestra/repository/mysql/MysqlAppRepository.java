package io.kestra.repository.mysql;

import io.kestra.core.models.apps.App;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAppRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAppRepository extends AbstractJdbcAppRepository {
    @Inject
    public MysqlAppRepository(@Named("apps") MysqlRepository<App> repository) {
        super(repository);
    }
}
