CREATE TABLE IF NOT EXISTS test_suites (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "flow_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_test_suites_deleted_tenant_namespace_id ON test_suites ("deleted", "tenant_id", "namespace", "id");

CREATE TABLE IF NOT EXISTS test_suite_runs (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "test_suite_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'testSuiteId') STORED,
    "start_date" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'startDate')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_test_suite_runs_suite_start_date ON test_suite_runs ("deleted", "tenant_id", "namespace", "test_suite_id", "start_date");
