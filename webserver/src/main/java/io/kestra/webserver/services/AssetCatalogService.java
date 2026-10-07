package io.kestra.webserver.services;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetLineageEdge;
import io.kestra.core.models.assets.AssetUsage;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.AssetLineageRepositoryInterface;
import io.kestra.core.repositories.AssetRepositoryInterface;
import io.kestra.core.repositories.AssetUsageRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.tenant.TenantService;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Browses and edits the asset catalog, checking the {@link Permission#ASSET} permission on each asset's namespace.
 * An asset without a namespace is global: anyone with ASSET access can read it, only instance-wide grants can change it.
 */
@Singleton
public class AssetCatalogService {
    private static final Set<Action> READ_ACTIONS = Set.of(Action.VIEW, Action.LIST);

    private final AssetRepositoryInterface assetRepository;
    private final AssetUsageRepositoryInterface assetUsageRepository;
    private final AssetLineageRepositoryInterface assetLineageRepository;
    private final ModelValidator modelValidator;
    private final TenantService tenantService;

    @Inject
    public AssetCatalogService(
        AssetRepositoryInterface assetRepository,
        AssetUsageRepositoryInterface assetUsageRepository,
        AssetLineageRepositoryInterface assetLineageRepository,
        ModelValidator modelValidator,
        TenantService tenantService) {
        this.assetRepository = assetRepository;
        this.assetUsageRepository = assetUsageRepository;
        this.assetLineageRepository = assetLineageRepository;
        this.modelValidator = modelValidator;
        this.tenantService = tenantService;
    }

    public ArrayListTotal<Asset> search(UserGrants grants, Pageable pageable, @Nullable String query, @Nullable String namespace, @Nullable String type) {
        return assetRepository.find(pageable, tenant(), grants.scope(Permission.ASSET, READ_ACTIONS), query, namespace, type);
    }

    public Asset get(UserGrants grants, String id) {
        Asset asset = find(id);
        require(grants, READ_ACTIONS, asset.getNamespace());
        return asset;
    }

    public Asset save(UserGrants grants, String source) {
        Asset asset;
        try {
            asset = JacksonMapper.ofYaml().readValue(source, Asset.class);
        } catch (JsonProcessingException e) {
            throw new ValidationErrorException(List.of("The asset is not valid YAML: %s".formatted(e.getOriginalMessage())));
        }
        if (asset == null) {
            throw new ValidationErrorException(List.of("The asset definition is empty."));
        }
        asset.withTenantId(tenant());
        modelValidator.validate(asset);

        Optional<Asset> previous = assetRepository.findById(tenant(), asset.getId());
        require(grants, Set.of(previous.isPresent() ? Action.UPDATE : Action.CREATE), asset.getNamespace());
        previous.ifPresent(existing -> require(grants, Set.of(Action.UPDATE), existing.getNamespace()));
        return assetRepository.save(asset.toUpdated(previous.orElse(null)));
    }

    public void delete(UserGrants grants, String id) {
        Asset asset = find(id);
        require(grants, Set.of(Action.DELETE), asset.getNamespace());
        assetRepository.save(asset.toDeleted());
    }

    public ArrayListTotal<AssetUsage> usages(UserGrants grants, Pageable pageable, String id) {
        get(grants, id);
        return assetUsageRepository.find(pageable, tenant(), id);
    }

    /**
     * The assets upstream and downstream of {@code id}, up to {@code depth} hops each way, leaving out the ones the caller cannot read.
     */
    public Lineage lineage(UserGrants grants, String id, int depth) {
        Asset root = get(grants, id);
        Map<String, Asset> assets = new LinkedHashMap<>();
        assets.put(root.getId(), root);
        Set<AssetLineageEdge> edges = new HashSet<>();
        walk(grants, root.getId(), depth, true, assets, edges);
        walk(grants, root.getId(), depth, false, assets, edges);
        List<Edge> visibleEdges = edges.stream()
            .filter(edge -> assets.containsKey(edge.getSourceId()) && assets.containsKey(edge.getTargetId()))
            .map(edge -> new Edge(edge.getSourceId(), edge.getTargetId(), edge.getNamespace(), edge.getFlowId(), edge.getTaskId()))
            .toList();
        return new Lineage(new ArrayList<>(assets.values()), visibleEdges);
    }

    private void walk(UserGrants grants, String rootId, int depth, boolean upstream, Map<String, Asset> assets, Set<AssetLineageEdge> edges) {
        Deque<String> frontier = new ArrayDeque<>(List.of(rootId));
        Set<String> visited = new HashSet<>(frontier);
        for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
            Deque<String> next = new ArrayDeque<>();
            for (String current : frontier) {
                List<AssetLineageEdge> neighbours = upstream
                    ? assetLineageRepository.findByTarget(tenant(), current)
                    : assetLineageRepository.findBySource(tenant(), current);
                for (AssetLineageEdge edge : neighbours) {
                    String neighbour = upstream ? edge.getSourceId() : edge.getTargetId();
                    edges.add(edge);
                    if (visited.add(neighbour)) {
                        assetRepository.findById(tenant(), neighbour)
                            .filter(asset -> grants.allows(Permission.ASSET, READ_ACTIONS, asset.getNamespace()))
                            .ifPresent(asset -> {
                                assets.put(asset.getId(), asset);
                                next.add(asset.getId());
                            });
                    }
                }
            }
            frontier = next;
        }
    }

    private void require(UserGrants grants, Set<Action> actions, @Nullable String namespace) {
        boolean allowed = namespace == null && !READ_ACTIONS.containsAll(actions)
            ? grants.allowsOnInstance(Permission.ASSET, actions)
            : grants.allows(Permission.ASSET, actions, namespace);
        if (!allowed) {
            throw new ForbiddenException("The user needs one of the actions %s on ASSET%s.".formatted(
                actions.stream().map(Action::name).sorted().toList(),
                namespace == null ? " on the whole instance" : " in namespace '%s'".formatted(namespace)
            ));
        }
    }

    private Asset find(String id) {
        return assetRepository.findById(tenant(), id)
            .orElseThrow(() -> new NotFoundException("The asset '%s' does not exist.".formatted(id)));
    }

    private String tenant() {
        return tenantService.resolveTenant();
    }

    public record Lineage(List<Asset> assets, List<Edge> edges) {
    }

    public record Edge(String source, String target, @Nullable String namespace, @Nullable String flowId, @Nullable String taskId) {
    }
}
