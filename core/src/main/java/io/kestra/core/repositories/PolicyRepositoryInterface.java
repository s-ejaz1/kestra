package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.policies.Policy;

public interface PolicyRepositoryInterface {
    Optional<Policy> findById(String tenantId, String id);

    List<Policy> findAll(String tenantId);

    Policy save(Policy policy);
}
