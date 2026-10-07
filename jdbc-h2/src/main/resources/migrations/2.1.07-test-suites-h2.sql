CREATE TABLE IF NOT EXISTS test_suites (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "flow_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_test_suites_deleted_tenant_namespace_id ON test_suites ("deleted", "tenant_id", "namespace", "id");

CREATE TABLE IF NOT EXISTS test_suite_runs (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "test_suite_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.testSuiteId')),
    "start_date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.startDate'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_test_suite_runs_suite_start_date ON test_suite_runs ("deleted", "tenant_id", "namespace", "test_suite_id", "start_date");
