package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.AccessScope;
import io.kestra.core.models.cases.Case;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

public interface CaseRepositoryInterface {
    Optional<Case> findById(String tenantId, String id);

    /**
     * Cases without a namespace are only visible to a {@link AccessScope.Kind#GLOBAL} scope.
     */
    ArrayListTotal<Case> find(
        Pageable pageable,
        String tenantId,
        AccessScope scope,
        @Nullable String query,
        @Nullable List<Case.Status> statuses,
        @Nullable Case.Severity severity,
        @Nullable String namespace);

    /**
     * The open cases set to link the failed executions of the flow automatically.
     */
    List<Case> findOpenAutoLinked(String tenantId, String namespace, String flowId);

    Case save(Case value);
}
