CREATE TABLE IF NOT EXISTS audit_logs (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "user_email" VARCHAR(256) NOT NULL GENERATED ALWAYS AS (value ->> 'userEmail') STORED,
    "resource" VARCHAR(50) GENERATED ALWAYS AS (value ->> 'resource') STORED,
    "date" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_audit_logs_deleted_tenant_date ON audit_logs ("deleted", "tenant_id", "date");
