CREATE TABLE IF NOT EXISTS secret_metadata (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "name" VARCHAR(350) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_secret_metadata_deleted_tenant_namespace_name ON secret_metadata ("deleted", "tenant_id", "namespace", "name");
