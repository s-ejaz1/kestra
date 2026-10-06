package io.kestra.webserver.filter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.reactivestreams.Publisher;

import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.Permission;
import io.kestra.webserver.controllers.ErrorController;
import io.kestra.webserver.controllers.RootController;
import io.kestra.webserver.controllers.api.*;
import io.kestra.webserver.services.AuthenticatedUser;
import io.kestra.webserver.services.IamService;
import io.kestra.webserver.services.UserGrants;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Checks that the authenticated caller holds the permission an API route needs: the resource comes from the
 * controller, the action from the HTTP method, and the namespace from the {@code namespace} path variable when the
 * route has one. Routes of a controller missing from the mapping are reserved to instance administrators.
 */
@Filter(Filter.MATCH_ALL_PATTERN)
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true")
public class IamAuthorizationFilter implements HttpServerFilter {
    private static final Set<Action> READ_ACTIONS = Set.of(Action.VIEW, Action.LIST);
    private static final Set<Action> ADMIN_ACTIONS = Set.of(Action.UPDATE);
    /** POST routes that only read data, because their parameters do not fit in a query string. */
    private static final Pattern READ_POST_ROUTE = Pattern.compile(".*/(autocomplete|latest|graph|validate(/.*)?|preview|export(/.*)?)$");

    private static final Set<Class<?>> OPEN_CONTROLLERS = Set.of(
        MiscController.class,
        IamController.class,
        RootController.class,
        ErrorController.class,
        UiController.class,
        ApiController.class,
        RedirectController.class
    );

    private static final Set<Class<?>> READ_OPEN_CONTROLLERS = Set.of(
        PluginController.class,
        BlueprintController.class
    );

    private static final Map<Class<?>, Permission> PERMISSIONS = Map.ofEntries(
        Map.entry(FlowController.class, Permission.FLOW),
        Map.entry(ExpressionController.class, Permission.FLOW),
        Map.entry(ExecutionController.class, Permission.EXECUTION),
        Map.entry(LogController.class, Permission.EXECUTION),
        Map.entry(MetricController.class, Permission.EXECUTION),
        Map.entry(OutputController.class, Permission.EXECUTION),
        Map.entry(PluginEndpointController.class, Permission.EXECUTION),
        Map.entry(TriggerController.class, Permission.TRIGGER),
        Map.entry(KVController.class, Permission.KVSTORE),
        Map.entry(NamespaceController.class, Permission.NAMESPACE),
        Map.entry(NamespaceFileController.class, Permission.NAMESPACE),
        Map.entry(SecretController.class, Permission.SECRET),
        Map.entry(NamespaceSecretController.class, Permission.SECRET),
        Map.entry(DashboardController.class, Permission.DASHBOARD),
        Map.entry(AiController.class, Permission.COPILOT),
        Map.entry(AiAgentController.class, Permission.COPILOT),
        Map.entry(McpServerController.class, Permission.MCP_SERVER),
        Map.entry(McpToolController.class, Permission.MCP_SERVER)
    );

    private final IamService iamService;

    @Inject
    public IamAuthorizationFilter(IamService iamService) {
        this.iamService = iamService;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.after();
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<AuthenticatedUser> user = AuthenticatedUser.from(request);
        if (user.isEmpty() || user.get().superAdmin()) {
            return chain.proceed(request);
        }

        return Mono.fromCallable(() -> isAllowed(request, user.get()))
            .subscribeOn(Schedulers.boundedElastic())
            .flux()
            .flatMap(allowed -> allowed ? chain.proceed(request) : Mono.just(HttpResponse.status(HttpStatus.FORBIDDEN)));
    }

    @SuppressWarnings("rawtypes")
    private boolean isAllowed(HttpRequest<?> request, AuthenticatedUser user) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isEmpty() || !(routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method)) {
            return true;
        }

        Class<?> controller = method.getDeclaringType();
        boolean isRead = HttpMethod.GET == request.getMethod() || HttpMethod.HEAD == request.getMethod();
        if (OPEN_CONTROLLERS.contains(controller) || (isRead && READ_OPEN_CONTROLLERS.contains(controller))) {
            return true;
        }

        UserGrants grants = UserGrants.of(request, user, iamService);
        Permission permission = PERMISSIONS.get(controller);
        if (permission == null) {
            return grants.allowsOnInstance(Permission.ROLE, ADMIN_ACTIONS);
        }

        String namespace = null;
        Set<Action> actions = actions(request.getMethod());
        if (routeMatch.get() instanceof UriRouteMatch<?, ?> uriRouteMatch) {
            namespace = Optional.ofNullable(uriRouteMatch.getVariableValues().get("namespace")).map(Object::toString).orElse(null);
            if (isReadOnlyPost(request.getMethod(), uriRouteMatch)) {
                actions = READ_ACTIONS;
            }
        }
        return grants.allows(permission, actions, namespace);
    }

    static Optional<Permission> permissionOf(Class<?> controller) {
        return Optional.ofNullable(PERMISSIONS.get(controller));
    }

    static boolean isReadOnlyPost(HttpMethod method, UriRouteMatch<?, ?> routeMatch) {
        return HttpMethod.POST == method && READ_POST_ROUTE.matcher(routeMatch.getRouteInfo().getUriMatchTemplate().toPathString()).matches();
    }

    private static Set<Action> actions(HttpMethod method) {
        return switch (method) {
            case GET, HEAD -> READ_ACTIONS;
            case POST -> Set.of(Action.CREATE);
            case PUT, PATCH -> Set.of(Action.UPDATE);
            case DELETE -> Set.of(Action.DELETE);
            default -> Set.of();
        };
    }
}
