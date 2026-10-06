package io.kestra.webserver.filter;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.reactivestreams.Publisher;

import io.kestra.core.models.audit.AuditLog;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.repositories.AuditLogRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.AuthenticatedUser;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Records every successful change made through the API by an authenticated caller. Request bodies are never
 * recorded, since they can hold secret values or passwords.
 */
@Slf4j
@Filter(Filter.MATCH_ALL_PATTERN)
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true")
public class AuditLogFilter implements HttpServerFilter {
    private static final Set<HttpMethod> CHANGES = Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    private final AuditLogRepositoryInterface auditLogRepository;
    private final TenantService tenantService;

    @Inject
    public AuditLogFilter(AuditLogRepositoryInterface auditLogRepository, TenantService tenantService) {
        this.auditLogRepository = auditLogRepository;
        this.tenantService = tenantService;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.after() + 1;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (!CHANGES.contains(request.getMethod())) {
            return chain.proceed(request);
        }

        return Flux.from(chain.proceed(request)).doOnNext(response ->
        {
            Optional<AuthenticatedUser> user = AuthenticatedUser.from(request);
            if (user.isPresent() && response.getStatus().getCode() < 300) {
                toAuditLog(request, user.get(), response.getStatus().getCode()).ifPresent(this::saveAsync);
            }
        });
    }

    @SuppressWarnings("rawtypes")
    private Optional<AuditLog> toAuditLog(HttpRequest<?> request, AuthenticatedUser user, int status) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        String resource = null;
        String namespace = null;
        if (routeMatch.isPresent() && routeMatch.get() instanceof UriRouteMatch<?, ?> uriRouteMatch) {
            if (IamAuthorizationFilter.isReadOnlyPost(request.getMethod(), uriRouteMatch)) {
                return Optional.empty();
            }
            namespace = Optional.ofNullable(uriRouteMatch.getVariableValues().get("namespace")).map(Object::toString).orElse(null);
        }
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method) {
            resource = IamAuthorizationFilter.permissionOf(method.getDeclaringType()).map(Permission::name).orElse(null);
        }

        return Optional.of(
            AuditLog.builder()
                .tenantId(tenantService.resolveTenant())
                .id(IdUtils.create())
                .date(Instant.now())
                .userId(user.id())
                .userEmail(user.email())
                .method(request.getMethodName())
                .path(request.getPath())
                .resource(resource)
                .namespace(namespace)
                .status(status)
                .build()
        );
    }

    private void saveAsync(AuditLog auditLog) {
        Mono.fromRunnable(() -> auditLogRepository.save(auditLog))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(null, e -> log.error("Cannot save the audit log of {} {}.", auditLog.getMethod(), auditLog.getPath(), e));
    }
}
