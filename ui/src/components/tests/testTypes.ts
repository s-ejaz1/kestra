export type TestState = "SUCCESS" | "FAILED" | "ERROR" | "SKIPPED"

export interface TestSuite {
    id: string;
    namespace: string;
    flowId: string;
    description?: string;
    disabled?: boolean;
    source: string;
}

export interface AssertionResult {
    operator: string;
    expected: unknown;
    actual: unknown;
    isSuccess: boolean;
    taskId?: string;
    description?: string;
    errorMessage?: string;
}

export interface UnitTestResult {
    testId: string;
    testType: string;
    executionId?: string;
    state: TestState;
    assertionResults: AssertionResult[];
    errors: {message: string; details?: string}[];
}

export interface TestSuiteRunResult {
    id: string;
    testSuiteId: string;
    namespace: string;
    flowId: string;
    state: TestState;
    startDate: string;
    endDate: string;
    results: UnitTestResult[];
}

export interface TestSuiteWithLastRun {
    testSuite: TestSuite;
    lastRun?: TestSuiteRunResult;
}

export const TEST_STATE_TAG: Record<TestState, "success" | "danger" | "warning" | "info"> = {
    SUCCESS: "success",
    FAILED: "danger",
    ERROR: "warning",
    SKIPPED: "info",
}

export const TEST_SUITE_TEMPLATE = `id: my-flow-tests
namespace: company.team
flowId: my-flow
testCases:
  - id: greets_in_urdu
    type: io.kestra.core.tests.flow.UnitTest
    description: The greeting uses the Urdu salutation
    fixtures:
      inputs:
        name: Salman
        language: Urdu
      tasks:
        - id: wait
          description: Skip the sleep
    assertions:
      - value: "{{ outputs.greeting.value }}"
        equalTo: Assalam o Alaikum, Salman!
  - id: greets_in_english
    type: io.kestra.core.tests.flow.UnitTest
    fixtures:
      inputs:
        name: Salman
        language: English
      tasks:
        - id: wait
    assertions:
      - value: "{{ outputs.greeting.value }}"
        startsWith: Hello
`
