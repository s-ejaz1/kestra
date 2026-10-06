package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.iam.IamRole;

public interface IamRoleRepositoryInterface {
    Optional<IamRole> findById(String tenantId, String id);

    List<IamRole> findAll(String tenantId);

    IamRole save(IamRole role);

    default IamRole delete(IamRole role) {
        return this.save(role.toDeleted());
    }
}
