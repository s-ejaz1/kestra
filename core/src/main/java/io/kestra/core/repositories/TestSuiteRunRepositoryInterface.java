package io.kestra.core.repositories;

import java.util.Optional;

import io.kestra.core.test.TestSuiteRunEntity;

public interface TestSuiteRunRepositoryInterface {
    TestSuiteRunEntity save(TestSuiteRunEntity run);

    Optional<TestSuiteRunEntity> findLatest(String tenantId, String namespace, String testSuiteId);
}
