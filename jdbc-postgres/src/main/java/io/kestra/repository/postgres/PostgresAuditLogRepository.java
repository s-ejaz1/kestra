package io.kestra.repository.postgres;

import io.kestra.core.models.audit.AuditLog;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAuditLogRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@PostgresRepositoryEnabled
public class PostgresAuditLogRepository extends AbstractJdbcAuditLogRepository {
    @Inject
    public PostgresAuditLogRepository(@Named("auditLogs") PostgresRepository<AuditLog> repository) {
        super(repository);
    }
}
