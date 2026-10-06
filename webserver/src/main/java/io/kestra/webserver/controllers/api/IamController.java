package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.models.iam.IamRole;
import io.kestra.core.models.iam.IamUser;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.services.AuthenticatedUser;
import io.kestra.webserver.services.IamService;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Controller("/api/v1/{tenant}")
public class IamController {
    private final IamService iamService;

    @Inject
    public IamController(IamService iamService) {
        this.iamService = iamService;
    }

    @Get("/me")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Retrieve the current user and the permissions granted to them")
    public Me getMe(HttpRequest<?> request) {
        AuthenticatedUser user = currentUser(request);
        List<Grant> grants = iamService.grants(user).entrySet().stream()
            .map(entry -> new Grant(entry.getKey(), entry.getValue()))
            .toList();
        return new Me(user.id(), user.email(), user.superAdmin(), grants);
    }

    @Get("/iam/users")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "List users")
    public PagedResults<ApiUser> listUsers(HttpRequest<?> request) {
        require(request, Permission.USER, Action.LIST);
        return paged(iamService.listUsers().stream().map(ApiUser::of).toList());
    }

    @Post("/iam/users")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Create a user")
    public ApiUser createUser(HttpRequest<?> request, @RequestBody(description = "The user") @Valid @Body UserRequest user) {
        require(request, Permission.USER, Action.CREATE);
        return ApiUser.of(iamService.createUser(user.email(), user.firstName(), user.lastName(), user.password()));
    }

    @Put("/iam/users/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Update a user; the password is only changed when given")
    public ApiUser updateUser(
        HttpRequest<?> request,
        @Parameter(description = "The user id") @PathVariable String id,
        @RequestBody(description = "The user") @Body UserUpdateRequest user) {
        require(request, Permission.USER, Action.UPDATE);
        return ApiUser.of(iamService.updateUser(id, user.firstName(), user.lastName(), user.disabled(), user.password()));
    }

    @Delete("/iam/users/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Delete a user and their role bindings")
    public HttpResponse<Void> deleteUser(HttpRequest<?> request, @Parameter(description = "The user id") @PathVariable String id) {
        require(request, Permission.USER, Action.DELETE);
        iamService.deleteUser(id);
        return HttpResponse.noContent();
    }

    @Get("/iam/roles")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "List roles, built-in ones first")
    public PagedResults<IamRole> listRoles(HttpRequest<?> request) {
        require(request, Permission.ROLE, Action.LIST);
        return paged(iamService.listRoles());
    }

    @Post("/iam/roles")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Create a role")
    public IamRole createRole(HttpRequest<?> request, @RequestBody(description = "The role") @Valid @Body RoleRequest role) {
        require(request, Permission.ROLE, Action.CREATE);
        return iamService.createRole(role.name(), role.description(), role.permissions());
    }

    @Put("/iam/roles/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Update a custom role")
    public IamRole updateRole(
        HttpRequest<?> request,
        @Parameter(description = "The role id") @PathVariable String id,
        @RequestBody(description = "The role") @Valid @Body RoleRequest role) {
        require(request, Permission.ROLE, Action.UPDATE);
        return iamService.updateRole(id, role.name(), role.description(), role.permissions());
    }

    @Delete("/iam/roles/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Delete a custom role and its bindings")
    public HttpResponse<Void> deleteRole(HttpRequest<?> request, @Parameter(description = "The role id") @PathVariable String id) {
        require(request, Permission.ROLE, Action.DELETE);
        iamService.deleteRole(id);
        return HttpResponse.noContent();
    }

    @Get("/iam/bindings")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "List role bindings")
    public PagedResults<IamBinding> listBindings(HttpRequest<?> request) {
        require(request, Permission.BINDING, Action.LIST);
        return paged(iamService.listBindings());
    }

    @Post("/iam/bindings")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Grant a role to a user, on the whole instance or on a namespace")
    public IamBinding createBinding(HttpRequest<?> request, @RequestBody(description = "The binding") @Valid @Body BindingRequest binding) {
        require(request, Permission.BINDING, Action.CREATE);
        return iamService.createBinding(binding.userId(), binding.roleId(), binding.namespace());
    }

    @Delete("/iam/bindings/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "IAM" }, summary = "Delete a role binding")
    public HttpResponse<Void> deleteBinding(HttpRequest<?> request, @Parameter(description = "The binding id") @PathVariable String id) {
        require(request, Permission.BINDING, Action.DELETE);
        iamService.deleteBinding(id);
        return HttpResponse.noContent();
    }

    private void require(HttpRequest<?> request, Permission permission, Action action) {
        iamService.requireAllowed(currentUser(request), permission, action, null);
    }

    private static AuthenticatedUser currentUser(HttpRequest<?> request) {
        return AuthenticatedUser.from(request)
            .orElseThrow(() -> new ForbiddenException("The request is not authenticated."));
    }

    private static <T> PagedResults<T> paged(List<T> items) {
        return PagedResults.of(new ArrayListTotal<>(items, items.size()));
    }

    public record Me(String id, String email, boolean superAdmin, List<Grant> grants) {
    }

    public record Grant(@Nullable String namespace, Map<Permission, Set<Action>> permissions) {
    }

    public record ApiUser(String id, String email, @Nullable String firstName, @Nullable String lastName, boolean disabled) {
        static ApiUser of(IamUser user) {
            return new ApiUser(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.isDisabled());
        }
    }

    public record UserRequest(@NotBlank String email, @Nullable String firstName, @Nullable String lastName, @NotBlank String password) {
    }

    public record UserUpdateRequest(@Nullable String firstName, @Nullable String lastName, boolean disabled, @Nullable String password) {
    }

    public record RoleRequest(@NotBlank String name, @Nullable String description, Map<Permission, List<Action>> permissions) {
    }

    public record BindingRequest(@NotBlank String userId, @NotBlank String roleId, @Nullable String namespace) {
    }
}
