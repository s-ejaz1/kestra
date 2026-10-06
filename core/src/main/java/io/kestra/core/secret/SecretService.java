package io.kestra.core.secret;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Strings;

import io.kestra.core.encryption.EncryptionConfig;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class SecretService<META> {
    private static final String SECRET_PREFIX = "SECRET_";

    @Nullable
    private final Provider<StorageInterface> storageInterface;

    @Nullable
    private final EncryptionConfig encryptionConfig;

    private Map<String, String> decodedSecrets;

    @Inject
    public SecretService(Provider<StorageInterface> storageInterface, EncryptionConfig encryptionConfig) {
        this.storageInterface = storageInterface;
        this.encryptionConfig = encryptionConfig;
    }

    /**
     * Creates a service that only resolves secrets from {@code SECRET_} environment variables.
     */
    protected SecretService() {
        this.storageInterface = null;
        this.encryptionConfig = null;
    }

    /**
     * Location of a stored secret's encrypted value in the internal storage. Keys are case-insensitive, as for
     * environment secrets.
     */
    public static URI storageUri(String namespace, String key) {
        return URI.create(StorageContext.KESTRA_PROTOCOL + StorageContext.secretsPrefix(namespace) + "/" + normalizeKey(key));
    }

    public static String normalizeKey(String key) {
        return key.toUpperCase();
    }

    public boolean isStoreEnabled() {
        return storageInterface != null && encryptionConfig != null && encryptionConfig.isConfigured();
    }

    @PostConstruct
    private void postConstruct() {
        this.decode();
    }

    public void decode() {
        decodedSecrets = System.getenv().entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(SECRET_PREFIX)).<Map.Entry<String, String>> mapMulti((entry, consumer) ->
            {
                try {
                    String value = entry.getValue().replaceAll("\\R", "");
                    consumer.accept(Map.entry(entry.getKey(), new String(Base64.getDecoder().decode(value))));
                } catch (Exception e) {
                    log.error("Could not decode secret '{}', make sure it is Base64-encoded: {}", entry.getKey(), e.getMessage());
                }
            })
            .collect(
                Collectors.toMap(
                    entry -> entry.getKey().substring(SECRET_PREFIX.length()).toUpperCase(),
                    Map.Entry::getValue
                )
            );
    }

    /**
     * Resolves a secret from the given namespace, then from each of its parents, then from the
     * {@code SECRET_} environment variables.
     */
    public String findSecret(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        Optional<String> stored = findStoredSecret(tenantId, namespace, key);
        if (stored.isPresent()) {
            return stored.get();
        }

        String secret = decodedSecrets.get(normalizeKey(key));
        if (secret == null) {
            throw new SecretNotFoundException("Cannot find secret for key '" + key + "'.");
        }
        return secret;
    }

    /**
     * Finds the secret in full mode, as a value plus metadata.
     * The default returns the value with empty metadata. Multi-field secret managers override this to add metadata.
     */
    public SecretObject findSecretObject(String tenantId, String namespace, String key) throws SecretNotFoundException, IOException {
        return new SecretObject(findSecret(tenantId, namespace, key));
    }

    public ArrayListTotal<META> list(Pageable pageable, String tenantId, List<QueryFilter> filters) throws IOException {
        //noinspection unchecked
        return ArrayListTotal.of(
            pageable,
            decodedSecrets.keySet().stream().filter(queryPredicate(filters)).map(s -> (META) s).toList()
        );
    }

    public Set<String> environmentSecretKeys() {
        return decodedSecrets.keySet();
    }

    /**
     * Matches secret keys against the {@code QUERY} filter, if any.
     */
    public static Predicate<String> queryPredicate(List<QueryFilter> filters) {
        return filters.stream()
            .filter(filter -> QueryFilter.Field.QUERY.equals(filter.field()) && filter.value() != null)
            .findFirst()
            .map(filter ->
            {
                if (QueryFilter.Op.EQUALS.equals(filter.operation())) {
                    return (Predicate<String>) s -> Strings.CI.contains(s, (String) filter.value());
                } else if (QueryFilter.Op.NOT_EQUALS.equals(filter.operation())) {
                    return (Predicate<String>) s -> !Strings.CI.contains(s, (String) filter.value());
                } else {
                    throw new IllegalArgumentException("Unsupported operation for QUERY filter: " + filter.operation());
                }
            })
            .orElse(s -> true);
    }

    public Map<String, Set<String>> inheritedSecrets(String tenantId, String namespace) throws IOException {
        return Map.of(namespace, decodedSecrets.keySet());
    }

    public Map<String, Set<String>> ownAndInheritedSecrets(String tenantId, String namespace) throws IOException {
        return inheritedSecrets(tenantId, namespace);
    }

    /**
     * Encrypts and writes the value of a stored secret, replacing any previous value.
     */
    public void putStoredSecret(String tenantId, String namespace, String key, String value) throws IOException {
        if (!isStoreEnabled()) {
            throw new SecretException(
                "Cannot store secret '%s': no encryption key is configured, set the '%s' property to enable stored secrets.".formatted(key, "kestra.encryption.secret-key")
            );
        }

        String encrypted;
        try {
            encrypted = EncryptionService.encrypt(encryptionConfig.get(), value);
        } catch (GeneralSecurityException e) {
            throw new SecretException("Cannot encrypt secret '%s'.".formatted(key), e);
        }

        try (InputStream content = new ByteArrayInputStream(encrypted.getBytes(StandardCharsets.UTF_8))) {
            storageInterface.get().put(tenantId, namespace, storageUri(namespace, key), content);
        }
    }

    public void deleteStoredSecret(String tenantId, String namespace, String key) throws IOException {
        if (storageInterface != null) {
            storageInterface.get().delete(tenantId, namespace, storageUri(namespace, key));
        }
    }

    private Optional<String> findStoredSecret(String tenantId, String namespace, String key) throws IOException {
        if (namespace == null || !isStoreEnabled()) {
            return Optional.empty();
        }

        List<String> namespaces = NamespaceInterface.asTree(namespace).reversed();
        for (String candidate : namespaces) {
            try (InputStream content = storageInterface.get().get(tenantId, candidate, storageUri(candidate, key))) {
                String encrypted = new String(content.readAllBytes(), StandardCharsets.UTF_8);
                return Optional.of(EncryptionService.decrypt(encryptionConfig.get(), encrypted));
            } catch (FileNotFoundException e) {
                // not defined at this level, look at the parent namespace
            } catch (GeneralSecurityException e) {
                throw new SecretException("Cannot decrypt secret '%s' of namespace '%s'; was the encryption key changed?".formatted(key, candidate), e);
            }
        }
        return Optional.empty();
    }
}
