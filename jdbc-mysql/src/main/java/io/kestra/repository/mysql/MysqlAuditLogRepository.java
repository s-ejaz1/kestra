package io.kestra.repository.mysql;

import io.kestra.core.models.audit.AuditLog;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAuditLogRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@MysqlRepositoryEnabled
public class MysqlAuditLogRepository extends AbstractJdbcAuditLogRepository {
    @Inject
    public MysqlAuditLogRepository(@Named("auditLogs") MysqlRepository<AuditLog> repository) {
        super(repository);
    }
}
