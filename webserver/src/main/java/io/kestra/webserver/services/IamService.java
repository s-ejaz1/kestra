package io.kestra.webserver.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.IamBinding;
import io.kestra.core.models.iam.IamRole;
import io.kestra.core.models.iam.IamUser;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.repositories.IamBindingRepositoryInterface;
import io.kestra.core.repositories.IamRoleRepositoryInterface;
import io.kestra.core.repositories.IamUserRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.AuthUtils;
import io.kestra.core.utils.IdUtils;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Manages IAM users, roles and role bindings, and resolves what a user is allowed to do.
 */
@Singleton
public class IamService {
    private static final Set<Permission> IAM_PERMISSIONS = EnumSet.of(Permission.USER, Permission.ROLE, Permission.BINDING);

    public static final List<IamRole> BUILT_IN_ROLES = List.of(
        builtIn("admin", "Admin", "Every action on every resource, including IAM.", allActions(EnumSet.allOf(Permission.class))),
        builtIn("editor", "Editor", "Every action on flows, executions, triggers, namespaces, KV store, secrets and dashboards.", allActions(EnumSet.complementOf(EnumSet.copyOf(IAM_PERMISSIONS)))),
        builtIn("launcher", "Launcher", "Read everything and run, follow, pause, resume, kill and restart executions.", launcherPermissions()),
        builtIn("viewer", "Viewer", "Read-only access to flows, executions, logs and outputs.", viewerPermissions())
    );

    private final IamUserRepositoryInterface userRepository;
    private final IamRoleRepositoryInterface roleRepository;
    private final IamBindingRepositoryInterface bindingRepository;
    private final TenantService tenantService;

    @Inject
    public IamService(
        IamUserRepositoryInterface userRepository,
        IamRoleRepositoryInterface roleRepository,
        IamBindingRepositoryInterface bindingRepository,
        TenantService tenantService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.bindingRepository = bindingRepository;
        this.tenantService = tenantService;
    }

    public List<IamUser> listUsers() {
        return userRepository.findAll(tenant());
    }

    public IamUser createUser(String email, @Nullable String firstName, @Nullable String lastName, String password) {
        String normalizedEmail = email == null ? null : email.trim();
        List<String> errors = new ArrayList<>();
        if (normalizedEmail == null || !BasicAuthService.EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            errors.add("The email '%s' is not a valid email address.".formatted(email));
        } else if (userRepository.findByEmail(tenant(), normalizedEmail).isPresent()) {
            errors.add("A user with the email '%s' already exists.".formatted(normalizedEmail));
        }
        validatePassword(password, errors);
        if (!errors.isEmpty()) {
            throw new ValidationErrorException(errors);
        }

        String salt = AuthUtils.generateSalt();
        return userRepository.save(
            IamUser.builder()
                .tenantId(tenant())
                .id(IdUtils.create())
                .email(normalizedEmail)
                .firstName(firstName)
                .lastName(lastName)
                .passwordSalt(salt)
                .passwordHash(AuthUtils.hashPassword(salt, password))
                .build()
        );
    }

    public IamUser updateUser(String id, @Nullable String firstName, @Nullable String lastName, boolean disabled, @Nullable String password) {
        IamUser user = findUser(id);
        IamUser.IamUserBuilder updated = user.toBuilder().firstName(firstName).lastName(lastName).disabled(disabled);
        if (password != null && !password.isEmpty()) {
            List<String> errors = new ArrayList<>();
            validatePassword(password, errors);
            if (!errors.isEmpty()) {
                throw new ValidationErrorException(errors);
            }
            String salt = AuthUtils.generateSalt();
            updated.passwordSalt(salt).passwordHash(AuthUtils.hashPassword(salt, password));
        }
        return userRepository.save(updated.build());
    }

    public void deleteUser(String id) {
        IamUser user = findUser(id);
        bindingRepository.findByUserId(tenant(), id).forEach(bindingRepository::delete);
        userRepository.delete(user);
    }

    public List<IamRole> listRoles() {
        return Stream.concat(BUILT_IN_ROLES.stream(), roleRepository.findAll(tenant()).stream()).toList();
    }

    public IamRole createRole(String name, @Nullable String description, Map<Permission, List<Action>> permissions) {
        validateRole(name, permissions);
        return roleRepository.save(
            IamRole.builder()
                .tenantId(tenant())
                .id(IdUtils.create())
                .name(name.trim())
                .description(description)
                .permissions(permissions)
                .build()
        );
    }

    public IamRole updateRole(String id, String name, @Nullable String description, Map<Permission, List<Action>> permissions) {
        IamRole role = findCustomRole(id);
        validateRole(name, permissions);
        return roleRepository.save(role.toBuilder().name(name.trim()).description(description).permissions(permissions).build());
    }

    public void deleteRole(String id) {
        IamRole role = findCustomRole(id);
        bindingRepository.findByRoleId(tenant(), id).forEach(bindingRepository::delete);
        roleRepository.delete(role);
    }

    public List<IamBinding> listBindings() {
        return bindingRepository.findAll(tenant());
    }

    public IamBinding createBinding(String userId, String roleId, @Nullable String namespace) {
        findUser(userId);
        findRole(roleId).orElseThrow(() -> new NotFoundException("The role '%s' does not exist.".formatted(roleId)));
        return bindingRepository.save(
            IamBinding.builder()
                .tenantId(tenant())
                .id(IdUtils.create())
                .userId(userId)
                .roleId(roleId)
                .namespace(namespace == null || namespace.isBlank() ? null : namespace.trim())
                .build()
        );
    }

    public void deleteBinding(String id) {
        IamBinding binding = bindingRepository.findById(tenant(), id)
            .orElseThrow(() -> new NotFoundException("The role binding '%s' does not exist.".formatted(id)));
        bindingRepository.delete(binding);
    }

    /**
     * The actions granted to a user, grouped by the namespace they apply to; the {@code null} key holds the actions
     * granted on the whole instance.
     */
    public Map<String, Map<Permission, Set<Action>>> grants(AuthenticatedUser user) {
        Map<String, Map<Permission, Set<Action>>> grants = new LinkedHashMap<>();
        if (user.superAdmin()) {
            grants.put(null, toSets(BUILT_IN_ROLES.getFirst().getPermissions()));
            return grants;
        }

        for (IamBinding binding : bindingRepository.findByUserId(tenant(), user.id())) {
            findRole(binding.getRoleId()).ifPresent(role ->
            {
                Map<Permission, Set<Action>> forNamespace = grants.computeIfAbsent(binding.getNamespace(), ns -> new EnumMap<>(Permission.class));
                role.getPermissions().forEach((permission, actions) -> forNamespace.computeIfAbsent(permission, p -> EnumSet.noneOf(Action.class)).addAll(actions));
            });
        }
        return grants;
    }

    /**
     * Whether the user may perform the action, on the given namespace, or on the whole instance when it is {@code null}.
     */
    public boolean isAllowed(AuthenticatedUser user, Permission permission, Action action, @Nullable String namespace) {
        if (user.superAdmin()) {
            return true;
        }
        return bindingRepository.findByUserId(tenant(), user.id()).stream()
            .filter(binding -> binding.appliesTo(namespace))
            .map(binding -> findRole(binding.getRoleId()))
            .flatMap(Optional::stream)
            .anyMatch(role -> role.allows(permission, action));
    }

    public void requireAllowed(AuthenticatedUser user, Permission permission, Action action, @Nullable String namespace) {
        if (!isAllowed(user, permission, action, namespace)) {
            throw new ForbiddenException(
                "The user '%s' is not allowed to %s %s%s.".formatted(user.email(), action, permission, namespace == null ? "" : " in namespace '%s'".formatted(namespace))
            );
        }
    }

    private Optional<IamRole> findRole(String id) {
        return BUILT_IN_ROLES.stream().filter(role -> role.getId().equals(id)).findFirst()
            .or(() -> roleRepository.findById(tenant(), id));
    }

    private IamRole findCustomRole(String id) {
        if (BUILT_IN_ROLES.stream().anyMatch(role -> role.getId().equals(id))) {
            throw new ValidationErrorException(List.of("The built-in role '%s' cannot be changed.".formatted(id)));
        }
        return roleRepository.findById(tenant(), id)
            .orElseThrow(() -> new NotFoundException("The role '%s' does not exist.".formatted(id)));
    }

    private IamUser findUser(String id) {
        return userRepository.findById(tenant(), id)
            .orElseThrow(() -> new NotFoundException("The user '%s' does not exist.".formatted(id)));
    }

    private static void validateRole(String name, Map<Permission, List<Action>> permissions) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add("The role name is required.");
        }
        if (permissions == null || permissions.containsKey(Permission.UNKNOWN)
            || permissions.values().stream().anyMatch(actions -> actions.contains(Action.UNKNOWN))) {
            errors.add("The role contains an unknown permission or action.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationErrorException(errors);
        }
    }

    private static void validatePassword(String password, List<String> errors) {
        if (password == null || !BasicAuthService.PASSWORD_PATTERN.matcher(password).matches()) {
            errors.add("The password must be at least 8 characters long and contain an uppercase letter, a lowercase letter and a digit.");
        }
    }

    private String tenant() {
        return tenantService.resolveTenant();
    }

    private static IamRole builtIn(String id, String name, String description, Map<Permission, List<Action>> permissions) {
        return IamRole.builder().id(id).name(name).description(description).permissions(permissions).builtIn(true).build();
    }

    private static Map<Permission, List<Action>> allActions(Set<Permission> permissions) {
        List<Action> actions = Arrays.stream(Action.values()).filter(action -> Action.UNKNOWN != action).toList();
        return permissions.stream()
            .filter(permission -> Permission.UNKNOWN != permission)
            .collect(Collectors.toMap(permission -> permission, permission -> actions, (a, b) -> a, () -> new EnumMap<>(Permission.class)));
    }

    private static Map<Permission, List<Action>> viewerPermissions() {
        List<Action> read = List.of(Action.VIEW, Action.LIST, Action.FOLLOW, Action.ACCESS_LOGS, Action.ACCESS_OUTPUTS, Action.ACCESS_FILES);
        return Arrays.stream(Permission.values())
            .filter(permission -> Permission.UNKNOWN != permission && !IAM_PERMISSIONS.contains(permission) && Permission.SECRET != permission)
            .collect(Collectors.toMap(permission -> permission, permission -> read, (a, b) -> a, () -> new EnumMap<>(Permission.class)));
    }

    private static Map<Permission, List<Action>> launcherPermissions() {
        Map<Permission, List<Action>> permissions = viewerPermissions();
        permissions.put(Permission.EXECUTION, List.of(
            Action.VIEW, Action.LIST, Action.FOLLOW, Action.ACCESS_LOGS, Action.ACCESS_OUTPUTS, Action.ACCESS_FILES,
            Action.CREATE, Action.EXECUTE, Action.RESTART, Action.RESUME, Action.PAUSE, Action.KILL
        ));
        permissions.put(Permission.FLOW, List.of(Action.VIEW, Action.LIST, Action.EXECUTE));
        return permissions;
    }

    private static Map<Permission, Set<Action>> toSets(Map<Permission, List<Action>> permissions) {
        Map<Permission, Set<Action>> sets = new EnumMap<>(Permission.class);
        permissions.forEach((permission, actions) -> sets.put(permission, EnumSet.copyOf(actions)));
        return sets;
    }
}
