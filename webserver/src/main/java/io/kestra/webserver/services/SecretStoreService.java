package io.kestra.webserver.services;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.models.secret.PersistedSecretMetadata;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.SecretMetadataRepositoryInterface;
import io.kestra.core.secret.SecretService;
import io.kestra.webserver.models.api.secret.ApiSecretMeta;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Manages the secrets stored by Kestra, and lists them together with the read-only {@code SECRET_} environment secrets.
 */
@Singleton
public class SecretStoreService {
    private final SecretService<String> secretService;
    private final SecretMetadataRepositoryInterface secretMetadataRepository;

    @Inject
    public SecretStoreService(SecretService<String> secretService, SecretMetadataRepositoryInterface secretMetadataRepository) {
        this.secretService = secretService;
        this.secretMetadataRepository = secretMetadataRepository;
    }

    public boolean isReadOnly() {
        return !secretService.isStoreEnabled();
    }

    /**
     * Environment secrets have no namespace, so they are only listed when the filters do not target one.
     */
    public ArrayListTotal<ApiSecretMeta> list(Pageable pageable, String tenantId, List<QueryFilter> filters) {
        boolean hasNamespaceFilter = filters.stream().anyMatch(filter -> QueryFilter.Field.NAMESPACE.equals(filter.field()));

        Stream<ApiSecretMeta> environmentSecrets = hasNamespaceFilter
            ? Stream.empty()
            : secretService.environmentSecretKeys().stream()
                .filter(SecretService.queryPredicate(filters))
                .map(ApiSecretMeta::ofEnvironment);

        Comparator<ApiSecretMeta> comparator = Comparator.comparing(ApiSecretMeta::getKey);
        if (pageable.getSort().getOrderBy().stream().findFirst().map(order -> Sort.Order.Direction.DESC == order.getDirection()).orElse(false)) {
            comparator = comparator.reversed();
        }

        List<ApiSecretMeta> secrets = Stream.concat(
                secretMetadataRepository.find(tenantId, filters).stream().map(ApiSecretMeta::of),
                environmentSecrets
            )
            .sorted(comparator)
            .toList();

        return ArrayListTotal.of(pageable, secrets);
    }

    public ApiSecretMeta put(String tenantId, String namespace, String key, String value, @Nullable String description, @Nullable List<PersistedSecretMetadata.Tag> tags) throws IOException {
        secretService.putStoredSecret(tenantId, namespace, key, value);

        PersistedSecretMetadata saved = secretMetadataRepository.save(
            PersistedSecretMetadata.builder()
                .tenantId(tenantId)
                .namespace(namespace)
                .name(SecretService.normalizeKey(key))
                .description(description)
                .tags(tags == null ? List.of() : tags)
                .build()
        );
        return ApiSecretMeta.of(saved);
    }

    public ApiSecretMeta patch(String tenantId, String namespace, String key, @Nullable String description, @Nullable List<PersistedSecretMetadata.Tag> tags) {
        PersistedSecretMetadata existing = findOrThrow(tenantId, namespace, key);

        PersistedSecretMetadata saved = secretMetadataRepository.save(
            existing.toBuilder()
                .description(description)
                .tags(tags == null ? List.of() : tags)
                .build()
        );
        return ApiSecretMeta.of(saved);
    }

    public void delete(String tenantId, String namespace, String key) throws IOException {
        PersistedSecretMetadata existing = findOrThrow(tenantId, namespace, key);

        secretMetadataRepository.delete(existing);
        secretService.deleteStoredSecret(tenantId, namespace, key);
    }

    /**
     * Keys usable from the given namespace, grouped by the namespace defining them. Environment secrets are reported
     * under the given namespace.
     */
    public Map<String, Set<String>> inheritedSecrets(String tenantId, String namespace) throws IOException {
        Map<String, Set<String>> secrets = new LinkedHashMap<>(secretService.inheritedSecrets(tenantId, namespace));

        List<String> parents = NamespaceInterface.asTree(namespace);
        for (String parent : parents.subList(0, parents.size() - 1)) {
            Set<String> keys = secretMetadataRepository.find(tenantId, List.of(namespaceFilter(parent))).stream()
                .map(PersistedSecretMetadata::getName)
                .collect(Collectors.toSet());
            if (!keys.isEmpty()) {
                secrets.put(parent, keys);
            }
        }
        return secrets;
    }

    private PersistedSecretMetadata findOrThrow(String tenantId, String namespace, String key) {
        return secretMetadataRepository.findByName(tenantId, namespace, SecretService.normalizeKey(key))
            .orElseThrow(() -> new NotFoundException("Secret '%s' does not exist in namespace '%s'.".formatted(key, namespace)));
    }

    private static QueryFilter namespaceFilter(String namespace) {
        return QueryFilter.builder()
            .field(QueryFilter.Field.NAMESPACE)
            .operation(QueryFilter.Op.EQUALS)
            .value(namespace)
            .build();
    }
}
