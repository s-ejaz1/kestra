package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.test.TestSuite;

public interface TestSuiteRepositoryInterface {
    Optional<TestSuite> findById(String tenantId, String namespace, String id);

    List<TestSuite> findAll(String tenantId);

    TestSuite save(TestSuite testSuite);

    default TestSuite delete(TestSuite testSuite) {
        return this.save(testSuite.toDeleted());
    }
}
