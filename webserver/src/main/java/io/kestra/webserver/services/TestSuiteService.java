package io.kestra.webserver.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.awaitility.core.ConditionTimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.ForbiddenException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.iam.Action;
import io.kestra.core.models.iam.Permission;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TestSuiteRepositoryInterface;
import io.kestra.core.repositories.TestSuiteRunRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunVariables;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.test.TestSuite;
import io.kestra.core.test.TestSuiteRunEntity;
import io.kestra.core.test.TestSuiteRunResult;
import io.kestra.core.test.TestSuiteUid;
import io.kestra.core.test.flow.Assertion;
import io.kestra.core.test.flow.AssertionResult;
import io.kestra.core.test.flow.AssertionRunError;
import io.kestra.core.test.flow.Fixtures;
import io.kestra.core.test.flow.UnitTest;
import io.kestra.core.test.flow.UnitTestResult;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Stores test suites declared in YAML and runs them: each test case starts a {@link ExecutionKind#TEST} execution of
 * the suite's flow with its fixtures, waits for it to end, then evaluates its assertions against that execution.
 */
@Slf4j
@Singleton
public class TestSuiteService {
    private static final Set<Action> READ_ACTIONS = Set.of(Action.VIEW, Action.LIST);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private final TestSuiteRepositoryInterface testSuiteRepository;
    private final TestSuiteRunRepositoryInterface testSuiteRunRepository;
    private final FlowRepositoryInterface flowRepository;
    private final ExecutionRepositoryInterface executionRepository;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final FlowInputOutput flowInputOutput;
    private final RunContextFactory runContextFactory;
    private final StorageInterface storageInterface;
    private final ModelValidator modelValidator;
    private final TenantService tenantService;
    private final Duration timeout;

    @Inject
    public TestSuiteService(
        TestSuiteRepositoryInterface testSuiteRepository,
        TestSuiteRunRepositoryInterface testSuiteRunRepository,
        FlowRepositoryInterface flowRepository,
        ExecutionRepositoryInterface executionRepository,
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        FlowInputOutput flowInputOutput,
        RunContextFactory runContextFactory,
        StorageInterface storageInterface,
        ModelValidator modelValidator,
        TenantService tenantService,
        @Value("${kestra.tests.timeout:PT5M}") Duration timeout) {
        this.testSuiteRepository = testSuiteRepository;
        this.testSuiteRunRepository = testSuiteRunRepository;
        this.flowRepository = flowRepository;
        this.executionRepository = executionRepository;
        this.executionCommandQueue = executionCommandQueue;
        this.flowInputOutput = flowInputOutput;
        this.runContextFactory = runContextFactory;
        this.storageInterface = storageInterface;
        this.modelValidator = modelValidator;
        this.tenantService = tenantService;
        this.timeout = timeout;
    }

    public List<TestSuite> list(UserGrants grants) {
        return testSuiteRepository.findAll(tenant()).stream()
            .filter(testSuite -> grants.allows(Permission.TEST, READ_ACTIONS, testSuite.getNamespace()))
            .toList();
    }

    public TestSuite get(UserGrants grants, String namespace, String id) {
        require(grants, READ_ACTIONS, namespace);
        return find(namespace, id);
    }

    public Optional<TestSuiteRunResult> lastRun(String namespace, String id) {
        return testSuiteRunRepository.findLatest(tenant(), namespace, id).map(TestSuiteRunEntity::toModel);
    }

    public TestSuite create(UserGrants grants, String source) {
        TestSuite testSuite = parse(source);
        require(grants, Set.of(Action.CREATE), testSuite.getNamespace());
        if (testSuiteRepository.findById(tenant(), testSuite.getNamespace(), testSuite.getId()).isPresent()) {
            throw new ValidationErrorException(List.of("A test suite '%s' already exists in namespace '%s'.".formatted(testSuite.getId(), testSuite.getNamespace())));
        }
        return testSuiteRepository.save(testSuite);
    }

    public TestSuite update(UserGrants grants, String namespace, String id, String source) {
        require(grants, Set.of(Action.UPDATE), namespace);
        find(namespace, id);
        TestSuite testSuite = parse(source);
        if (!namespace.equals(testSuite.getNamespace()) || !id.equals(testSuite.getId())) {
            throw new ValidationErrorException(List.of("The namespace and id of a test suite cannot be changed; create a new test suite instead."));
        }
        return testSuiteRepository.save(testSuite);
    }

    public void delete(UserGrants grants, String namespace, String id) {
        require(grants, Set.of(Action.DELETE), namespace);
        testSuiteRepository.delete(find(namespace, id));
    }

    public TestSuiteRunResult run(UserGrants grants, String namespace, String id) {
        require(grants, Set.of(Action.EXECUTE), namespace);
        TestSuite testSuite = find(namespace, id);
        String runId = IdUtils.create();

        TestSuiteRunResult result;
        if (testSuite.isDisabled()) {
            result = TestSuiteRunResult.ofDisabledTestSuite(runId, id, namespace, testSuite.getFlowId());
        } else {
            Flow flow = flowRepository.findById(tenant(), namespace, testSuite.getFlowId())
                .orElseThrow(() -> new NotFoundException("The flow '%s' does not exist in namespace '%s'.".formatted(testSuite.getFlowId(), namespace)));
            Instant startDate = Instant.now();
            List<UnitTestResult> results = testSuite.getTestCases().stream().map(testCase -> runTestCase(flow, testCase)).toList();
            result = TestSuiteRunResult.of(runId, id, namespace, testSuite.getFlowId(), startDate, Instant.now(), results);
        }

        testSuiteRunRepository.save(TestSuiteRunEntity.create(tenant(), new TestSuiteUid(tenant(), namespace, id), result));
        return result;
    }

    private UnitTestResult runTestCase(Flow flow, UnitTest testCase) {
        Fixtures fixtures = testCase.getFixtures();
        if (testCase.isDisabled()) {
            return UnitTestResult.ofDisabled(testCase.getId(), testCase.getType(), fixtures);
        }

        String executionId = IdUtils.create();
        try {
            Map<String, Object> inputs = flowInputOutput.readExecutionInputs(flow, executionId, Optional.ofNullable(fixtures).map(Fixtures::getInputs).orElse(Map.of()));
            executionCommandQueue.emit(
                Create.of(new ExecutionId(flow.getTenantId(), flow.getNamespace(), flow.getId(), executionId, flow.getRevision()))
                    .withKind(ExecutionKind.TEST)
                    .withInputs(inputs)
                    .withFixtures(fixtures == null ? null : fixtures.getTasks())
                    .withTrigger(trigger(fixtures))
                    .withVariables(variables(flow, executionId, fixtures))
            );
            Execution execution = Await.await()
                .atMost(timeout)
                .pollInterval(POLL_INTERVAL)
                .until(() -> executionRepository.findById(tenant(), executionId).filter(e -> e.getState().isTerminated()), Optional::isPresent)
                .orElseThrow();

            List<AssertionResult> results = new ArrayList<>();
            List<AssertionRunError> errors = new ArrayList<>();
            String actualState = execution.getState().getCurrent().name();
            String expectedState = Optional.ofNullable(testCase.getExpectedState()).orElse(State.Type.SUCCESS).name();
            results.add(new AssertionResult("equalTo", expectedState, actualState, expectedState.equals(actualState), null, "Execution state", null));

            RunContext runContext = runContextFactory.of(flow, execution);
            for (Assertion assertion : testCase.getAssertions()) {
                Assertion.AssertionRunResult run = assertion.run(runContext);
                results.addAll(run.results());
                errors.addAll(run.errors());
            }
            return UnitTestResult.of(testCase.getId(), testCase.getType(), executionId, null, results, errors, fixtures);
        } catch (ConditionTimeoutException e) {
            return failed(testCase, executionId, "The execution did not end within %s.".formatted(timeout), fixtures);
        } catch (Exception e) {
            log.warn("Test case '{}' of flow '{}' could not run.", testCase.getId(), flow.uid(), e);
            return failed(testCase, executionId, "The test case could not run: %s".formatted(e.getMessage()), fixtures);
        }
    }

    private static UnitTestResult failed(UnitTest testCase, String executionId, String message, @Nullable Fixtures fixtures) {
        return UnitTestResult.of(testCase.getId(), testCase.getType(), executionId, null, List.of(), List.of(new AssertionRunError(message, null)), fixtures);
    }

    @Nullable
    private static ExecutionTrigger trigger(@Nullable Fixtures fixtures) {
        if (fixtures == null || fixtures.getTrigger() == null) {
            return null;
        }
        return ExecutionTrigger.builder()
            .id(fixtures.getTrigger().getId())
            .type(fixtures.getTrigger().getType())
            .variables(fixtures.getTrigger().getVariables())
            .build();
    }

    /**
     * Writes the fixture files to the execution's storage and exposes their URIs as {@code files}, keeping the flow's own variables.
     */
    @Nullable
    private Map<String, Object> variables(Flow flow, String executionId, @Nullable Fixtures fixtures) throws IOException {
        if (fixtures == null || fixtures.getFiles() == null || fixtures.getFiles().isEmpty()) {
            return null;
        }
        URI base = StorageContext.forExecution(flow.getTenantId(), flow.getNamespace(), flow.getId(), executionId).getExecutionStorageURI(StorageContext.KESTRA_SCHEME);
        Map<String, String> files = new HashMap<>();
        for (Map.Entry<String, String> file : fixtures.getFiles().entrySet()) {
            URI uri = URI.create(base + "/fixtures/" + file.getKey());
            storageInterface.put(flow.getTenantId(), flow.getNamespace(), uri, new ByteArrayInputStream(file.getValue().getBytes(StandardCharsets.UTF_8)));
            files.put(file.getKey(), uri.toString());
        }
        Map<String, Object> variables = flow.getVariables() == null ? new HashMap<>() : new HashMap<>(flow.getVariables());
        variables.put(RunVariables.FIXTURE_FILES_KEY, files);
        return variables;
    }

    private TestSuite parse(String source) {
        TestSuite parsed;
        try {
            parsed = JacksonMapper.ofYaml().readValue(source, TestSuite.class);
        } catch (JsonProcessingException e) {
            throw new ValidationErrorException(List.of("The test suite is not valid YAML: %s".formatted(e.getOriginalMessage())));
        }
        if (parsed == null) {
            throw new ValidationErrorException(List.of("The test suite definition is empty."));
        }
        TestSuite testSuite = parsed.toBuilder().tenantId(tenant()).source(source).build();
        modelValidator.validate(testSuite);
        return testSuite;
    }

    private void require(UserGrants grants, Set<Action> actions, String namespace) {
        if (!grants.allows(Permission.TEST, actions, namespace)) {
            throw new ForbiddenException("The user needs one of the actions %s on TEST in namespace '%s'.".formatted(actions.stream().map(Action::name).sorted().toList(), namespace));
        }
    }

    private TestSuite find(String namespace, String id) {
        return testSuiteRepository.findById(tenant(), namespace, id)
            .orElseThrow(() -> new NotFoundException("The test suite '%s' does not exist in namespace '%s'.".formatted(id, namespace)));
    }

    private String tenant() {
        return tenantService.resolveTenant();
    }
}
