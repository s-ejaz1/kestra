package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.iam.IamUser;

public interface IamUserRepositoryInterface {
    Optional<IamUser> findById(String tenantId, String id);

    Optional<IamUser> findByEmail(String tenantId, String email);

    List<IamUser> findAll(String tenantId);

    IamUser save(IamUser user);

    default IamUser delete(IamUser user) {
        return this.save(user.toDeleted());
    }
}
