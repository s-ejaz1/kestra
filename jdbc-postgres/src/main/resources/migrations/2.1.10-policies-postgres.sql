CREATE TABLE IF NOT EXISTS policies (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "enforcement" VARCHAR(20) NOT NULL GENERATED ALWAYS AS (value ->> 'enforcement') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_policies_deleted_tenant_id ON policies ("deleted", "tenant_id", "id");
