package io.kestra.webserver.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.apps.App;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.repositories.AppRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Stores apps declared in YAML, checking that they are well-formed, that their flow exists, and that the caller holds
 * the {@link Permission#APP} permission on their namespace.
 */
@Singleton
public class AppService {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9._-]*");
    private static final Set<Action> READ_ACTIONS = Set.of(Action.VIEW, Action.LIST);

    private final AppRepositoryInterface appRepository;
    private final FlowRepositoryInterface flowRepository;
    private final TenantService tenantService;

    @Inject
    public AppService(AppRepositoryInterface appRepository, FlowRepositoryInterface flowRepository, TenantService tenantService) {
        this.appRepository = appRepository;
        this.flowRepository = flowRepository;
        this.tenantService = tenantService;
    }

    public List<App> list(UserGrants grants) {
        return appRepository.findAll(tenant()).stream()
            .filter(app -> grants.allows(Permission.APP, READ_ACTIONS, app.getNamespace()))
            .toList();
    }

    public App get(UserGrants grants, String namespace, String id) {
        require(grants, READ_ACTIONS, namespace);
        return find(namespace, id);
    }

    public App create(UserGrants grants, String source) {
        Definition definition = parse(source);
        require(grants, Set.of(Action.CREATE), definition.namespace());
        if (appRepository.findById(tenant(), definition.namespace(), definition.id()).isPresent()) {
            throw new ValidationErrorException(List.of("An app '%s' already exists in namespace '%s'.".formatted(definition.id(), definition.namespace())));
        }
        return appRepository.save(definition.toApp(tenant(), source).build());
    }

    public App update(UserGrants grants, String namespace, String id, String source) {
        require(grants, Set.of(Action.UPDATE), namespace);
        App existing = find(namespace, id);
        Definition definition = parse(source);
        if (!namespace.equals(definition.namespace()) || !id.equals(definition.id())) {
            throw new ValidationErrorException(List.of("The namespace and id of an app cannot be changed; create a new app instead."));
        }
        return appRepository.save(definition.toApp(tenant(), source).created(existing.getCreated()).build());
    }

    public void delete(UserGrants grants, String namespace, String id) {
        require(grants, Set.of(Action.DELETE), namespace);
        appRepository.delete(find(namespace, id));
    }

    private Definition parse(String source) {
        Definition definition;
        try {
            definition = JacksonMapper.ofYaml().readValue(source, Definition.class);
        } catch (JsonProcessingException e) {
            throw new ValidationErrorException(List.of("The app is not valid YAML: %s".formatted(e.getOriginalMessage())));
        }

        List<String> errors = new ArrayList<>();
        if (definition == null) {
            throw new ValidationErrorException(List.of("The app definition is empty."));
        }
        if (definition.id() == null || !ID_PATTERN.matcher(definition.id()).matches()) {
            errors.add("The app id is required and may only contain letters, digits, '.', '_' and '-'.");
        }
        if (definition.namespace() == null || definition.namespace().isBlank()) {
            errors.add("The app namespace is required.");
        }
        if (definition.flowId() == null || definition.flowId().isBlank()) {
            errors.add("The app flowId is required.");
        } else if (definition.namespace() != null && flowRepository.findById(tenant(), definition.namespace(), definition.flowId()).isEmpty()) {
            errors.add("The flow '%s' does not exist in namespace '%s'.".formatted(definition.flowId(), definition.namespace()));
        }
        List<App.Layout> layout = definition.layout() == null ? List.of() : definition.layout();
        if (layout.stream().noneMatch(stage -> App.Stage.OPEN == stage.on())) {
            errors.add("The app layout needs an 'on: OPEN' stage, shown before the flow runs.");
        }
        for (App.Layout stage : layout) {
            if (stage.on() == null || App.Stage.UNKNOWN == stage.on()) {
                errors.add("Each layout stage needs 'on' set to OPEN, RUNNING, SUCCESS or FAILURE.");
            }
            for (Map<String, Object> block : stage.blocks() == null ? List.<Map<String, Object>> of() : stage.blocks()) {
                Object type = block.get("type");
                if (!App.BLOCK_TYPES.contains(type)) {
                    errors.add("The block type '%s' is not supported; use one of %s.".formatted(type, App.BLOCK_TYPES));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationErrorException(errors);
        }
        return definition;
    }

    private void require(UserGrants grants, Set<Action> actions, String namespace) {
        if (!grants.allows(Permission.APP, actions, namespace)) {
            throw new ForbiddenException("The user needs one of the actions %s on APP in namespace '%s'.".formatted(actions.stream().map(Action::name).sorted().toList(), namespace));
        }
    }

    private App find(String namespace, String id) {
        return appRepository.findById(tenant(), namespace, id)
            .orElseThrow(() -> new NotFoundException("The app '%s' does not exist in namespace '%s'.".formatted(id, namespace)));
    }

    private String tenant() {
        return tenantService.resolveTenant();
    }

    private record Definition(
        String id,
        String namespace,
        @Nullable String displayName,
        @Nullable String description,
        String flowId,
        @Nullable List<App.Layout> layout) {

        App.AppBuilder toApp(String tenantId, String source) {
            return App.builder()
                .tenantId(tenantId)
                .id(id)
                .namespace(namespace)
                .displayName(displayName)
                .description(description)
                .flowId(flowId)
                .layout(layout == null ? List.of() : layout)
                .source(source);
        }
    }
}
