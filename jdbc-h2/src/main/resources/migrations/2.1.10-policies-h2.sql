CREATE TABLE IF NOT EXISTS policies (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "enforcement" VARCHAR(20) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.enforcement')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_policies_deleted_tenant_id ON policies ("deleted", "tenant_id", "id");
