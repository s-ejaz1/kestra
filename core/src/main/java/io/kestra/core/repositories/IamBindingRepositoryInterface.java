package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.iam.IamBinding;

public interface IamBindingRepositoryInterface {
    Optional<IamBinding> findById(String tenantId, String id);

    List<IamBinding> findByUserId(String tenantId, String userId);

    List<IamBinding> findByRoleId(String tenantId, String roleId);

    List<IamBinding> findAll(String tenantId);

    IamBinding save(IamBinding binding);

    default IamBinding delete(IamBinding binding) {
        return this.save(binding.toDeleted());
    }
}
