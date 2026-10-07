CREATE TABLE IF NOT EXISTS cases (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "title" VARCHAR(512) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.title')),
    "status" VARCHAR(20) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.status')),
    "severity" VARCHAR(20) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.severity')),
    "flow_id" VARCHAR(100) GENERATED ALWAYS AS (JQ_STRING("value", '.flowId')),
    "auto_link" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.autoLink')),
    "updated" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.updated'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_cases_deleted_tenant_updated ON cases ("deleted", "tenant_id", "updated");
CREATE INDEX IF NOT EXISTS ix_cases_auto_link ON cases ("deleted", "tenant_id", "namespace", "flow_id", "auto_link");

CREATE TABLE IF NOT EXISTS case_activities (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "case_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.caseId')),
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_case_activities_case_date ON case_activities ("deleted", "tenant_id", "case_id", "date");
