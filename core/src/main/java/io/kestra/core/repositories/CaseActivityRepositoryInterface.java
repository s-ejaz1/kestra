package io.kestra.core.repositories;

import io.kestra.core.models.cases.CaseActivity;

import io.micronaut.data.model.Pageable;

public interface CaseActivityRepositoryInterface {
    CaseActivity save(CaseActivity activity);

    /**
     * The timeline of a case, oldest first.
     */
    ArrayListTotal<CaseActivity> find(Pageable pageable, String tenantId, String caseId);
}
