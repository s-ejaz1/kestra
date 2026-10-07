CREATE TABLE IF NOT EXISTS assets (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (value ->> 'namespace') STORED,
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "type" VARCHAR(512) GENERATED ALWAYS AS (value ->> 'type') STORED,
    "display_name" VARCHAR(512) GENERATED ALWAYS AS (value ->> 'displayName') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_assets_deleted_tenant_id ON assets ("deleted", "tenant_id", "id");

CREATE TABLE IF NOT EXISTS asset_usages (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "asset_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'assetId') STORED,
    "date" TIMESTAMPTZ NOT NULL GENERATED ALWAYS AS (PARSE_ISO8601_DATETIME(value ->> 'date')) STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_asset_usages_asset_date ON asset_usages ("deleted", "tenant_id", "asset_id", "date");

CREATE TABLE IF NOT EXISTS asset_lineage (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "source_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'sourceId') STORED,
    "target_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (value ->> 'targetId') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_asset_lineage_source ON asset_lineage ("deleted", "tenant_id", "source_id");
CREATE INDEX IF NOT EXISTS ix_asset_lineage_target ON asset_lineage ("deleted", "tenant_id", "target_id");
