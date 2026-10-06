CREATE TABLE IF NOT EXISTS audit_logs (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "user_email" VARCHAR(256) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.userEmail')),
    "resource" VARCHAR(50) GENERATED ALWAYS AS (JQ_STRING("value", '.resource')),
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_audit_logs_deleted_tenant_date ON audit_logs ("deleted", "tenant_id", "date");
