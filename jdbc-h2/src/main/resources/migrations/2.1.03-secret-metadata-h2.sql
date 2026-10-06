CREATE TABLE IF NOT EXISTS secret_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_secret_metadata_deleted_tenant_namespace_name ON secret_metadata ("deleted", "tenant_id", "namespace", "name");
