package io.kestra.webserver.services;

import java.util.Map;
import java.util.Set;

import io.kestra.core.models.AccessScope;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.repositories.GlobalNamespaceAccessControl;
import io.kestra.core.repositories.NamespaceAccessControl;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Restricts what a repository returns to the namespaces the caller of the current API request may read. Work that is
 * not done for an API request (executor, scheduler, worker) keeps seeing every namespace.
 */
@Singleton
@Replaces(GlobalNamespaceAccessControl.class)
public class IamNamespaceAccessControl implements NamespaceAccessControl {
    private static final Set<Action> READ_ACTIONS = Set.of(Action.VIEW, Action.LIST);

    private static final Map<QueryFilter.Resource, Permission> PERMISSIONS = Map.of(
        QueryFilter.Resource.FLOW, Permission.FLOW,
        QueryFilter.Resource.EXECUTION, Permission.EXECUTION,
        QueryFilter.Resource.LOG, Permission.EXECUTION,
        QueryFilter.Resource.TASK, Permission.EXECUTION,
        QueryFilter.Resource.TRIGGER, Permission.TRIGGER,
        QueryFilter.Resource.KV_METADATA, Permission.KVSTORE,
        QueryFilter.Resource.SECRET_METADATA, Permission.SECRET,
        QueryFilter.Resource.NAMESPACE_FILE_METADATA, Permission.NAMESPACE
    );

    private final IamService iamService;

    @Inject
    public IamNamespaceAccessControl(IamService iamService) {
        this.iamService = iamService;
    }

    @Override
    public AccessScope namespaceScope(QueryFilter.Resource resource) {
        Permission permission = PERMISSIONS.get(resource);
        if (permission == null) {
            return AccessScope.global();
        }

        return ServerRequestContext.currentRequest()
            .flatMap(request -> AuthenticatedUser.from(request).map(user -> scope((HttpRequest<?>) request, user, permission)))
            .orElse(AccessScope.global());
    }

    private AccessScope scope(HttpRequest<?> request, AuthenticatedUser user, Permission permission) {
        return UserGrants.of(request, user, iamService).scope(permission, READ_ACTIONS);
    }
}
