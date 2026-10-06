package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.apps.App;

public interface AppRepositoryInterface {
    Optional<App> findById(String tenantId, String namespace, String id);

    List<App> findAll(String tenantId);

    App save(App app);

    default App delete(App app) {
        return this.save(app.toDeleted());
    }
}
