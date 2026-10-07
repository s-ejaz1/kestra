CREATE TABLE IF NOT EXISTS cases (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "title" VARCHAR(512) NOT NULL GENERATED ALWAYS AS (value ->> 'title') STORED,
    "status" VARCHAR(20) NOT NULL GENERATED ALWAYS AS (value ->> 'status') STORED,
    "severity" VARCHAR(20) NOT NULL GENERATED ALWAYS AS (value ->> 'severity') STORED,
    "flow_id" VARCHAR(100) GENERATED ALWAYS AS (value ->> 'flowId') STORED,
    "auto_link" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'autoLink' AS BOOL)) STORED,
    "updated" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'updated')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_cases_deleted_tenant_updated ON cases ("deleted", "tenant_id", "updated");
CREATE INDEX IF NOT EXISTS ix_cases_auto_link ON cases ("deleted", "tenant_id", "namespace", "flow_id", "auto_link");

CREATE TABLE IF NOT EXISTS case_activities (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "case_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'caseId') STORED,
    "date" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_case_activities_case_date ON case_activities ("deleted", "tenant_id", "case_id", "date");
