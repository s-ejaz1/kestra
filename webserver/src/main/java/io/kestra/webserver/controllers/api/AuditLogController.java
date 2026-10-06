package io.kestra.webserver.controllers.api;

import io.kestra.core.models.audit.AuditLog;
import io.kestra.core.repositories.AuditLogRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Controller("/api/v1/{tenant}/audit-logs")
public class AuditLogController {
    private final AuditLogRepositoryInterface auditLogRepository;
    private final TenantService tenantService;

    @Inject
    public AuditLogController(AuditLogRepositoryInterface auditLogRepository, TenantService tenantService) {
        this.auditLogRepository = auditLogRepository;
        this.tenantService = tenantService;
    }

    @Get("/search")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Audit Logs" }, summary = "Search audit logs, newest first")
    public PagedResults<AuditLog> searchAuditLogs(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "25") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size,
        @Parameter(description = "Only changes in this namespace and its children") @Nullable @QueryValue String namespace,
        @Parameter(description = "Only changes by users whose email contains this text") @Nullable @QueryValue String user,
        @Parameter(description = "Only changes on this resource, e.g. FLOW") @Nullable @QueryValue String resource) {
        return PagedResults.of(
            auditLogRepository.find(PageableUtils.from(page, size), tenantService.resolveTenant(), namespace, user, resource)
        );
    }
}
