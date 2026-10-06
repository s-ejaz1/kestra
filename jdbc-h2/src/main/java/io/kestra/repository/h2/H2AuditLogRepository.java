package io.kestra.repository.h2;

import io.kestra.core.models.audit.AuditLog;
import io.kestra.core.repositories.RepositoryBean;
import io.kestra.jdbc.repository.AbstractJdbcAuditLogRepository;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@RepositoryBean
@H2RepositoryEnabled
public class H2AuditLogRepository extends AbstractJdbcAuditLogRepository {
    @Inject
    public H2AuditLogRepository(@Named("auditLogs") H2Repository<AuditLog> repository) {
        super(repository);
    }
}
