package io.kestra.webserver.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kestra.core.models.AccessScope;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.Permission;

import io.micronaut.http.HttpRequest;
import jakarta.annotation.Nullable;

/**
 * The grants of the caller of a request, resolved once per request and kept as a request attribute.
 *
 * @param byNamespace actions granted per namespace; the {@code null} key holds the actions granted on the whole instance.
 */
public record UserGrants(boolean superAdmin, Map<String, Map<Permission, Set<Action>>> byNamespace) {
    private static final String REQUEST_ATTRIBUTE = "kestra.userGrants";

    public static UserGrants of(HttpRequest<?> request, AuthenticatedUser user, IamService iamService) {
        return request.getAttribute(REQUEST_ATTRIBUTE, UserGrants.class).orElseGet(() ->
        {
            UserGrants grants = new UserGrants(user.superAdmin(), iamService.grants(user));
            request.setAttribute(REQUEST_ATTRIBUTE, grants);
            return grants;
        });
    }

    /**
     * Whether one of the actions is granted on the namespace, or on at least one namespace when it is {@code null}.
     */
    public boolean allows(Permission permission, Set<Action> actions, @Nullable String namespace) {
        if (superAdmin) {
            return true;
        }
        return byNamespace.entrySet().stream()
            .filter(entry -> namespace == null || entry.getKey() == null || isInside(namespace, entry.getKey()))
            .anyMatch(entry -> entry.getValue().getOrDefault(permission, Set.of()).stream().anyMatch(actions::contains));
    }

    public boolean allowsOnInstance(Permission permission, Set<Action> actions) {
        return superAdmin
            || byNamespace.getOrDefault(null, Map.of()).getOrDefault(permission, Set.of()).stream().anyMatch(actions::contains);
    }

    public AccessScope scope(Permission permission, Set<Action> actions) {
        if (superAdmin) {
            return AccessScope.global();
        }
        List<String> namespaces = new ArrayList<>();
        for (Map.Entry<String, Map<Permission, Set<Action>>> entry : byNamespace.entrySet()) {
            if (entry.getValue().getOrDefault(permission, Set.of()).stream().noneMatch(actions::contains)) {
                continue;
            }
            if (entry.getKey() == null) {
                return AccessScope.global();
            }
            namespaces.add(entry.getKey());
        }
        return AccessScope.namespaces(namespaces);
    }

    private static boolean isInside(String namespace, String parent) {
        return namespace.equals(parent) || namespace.startsWith(parent + ".");
    }
}
