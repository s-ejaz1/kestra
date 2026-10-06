package io.kestra.core.repositories;

import io.kestra.core.models.audit.AuditLog;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

public interface AuditLogRepositoryInterface {
    AuditLog save(AuditLog auditLog);

    /**
     * Audit logs, newest first, optionally restricted to a namespace and its children, to a user email, and to a resource.
     */
    ArrayListTotal<AuditLog> find(Pageable pageable, String tenantId, @Nullable String namespace, @Nullable String userEmail, @Nullable String resource);
}
