CREATE TABLE IF NOT EXISTS assets (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "namespace" VARCHAR(150) GENERATED ALWAYS AS (JQ_STRING("value", '.namespace')),
    "id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "type" VARCHAR(512) GENERATED ALWAYS AS (JQ_STRING("value", '.type')),
    "display_name" VARCHAR(512) GENERATED ALWAYS AS (JQ_STRING("value", '.displayName')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_assets_deleted_tenant_id ON assets ("deleted", "tenant_id", "id");

CREATE TABLE IF NOT EXISTS asset_usages (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "asset_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.assetId')),
    "date" TIMESTAMP NOT NULL GENERATED ALWAYS AS (PARSEDATETIME(LEFT(JQ_STRING("value", '.date'), 23) || '+00:00', 'yyyy-MM-dd''T''HH:mm:ss.SSSXXX')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_asset_usages_asset_date ON asset_usages ("deleted", "tenant_id", "asset_id", "date");

CREATE TABLE IF NOT EXISTS asset_lineage (
    "key" VARCHAR(768) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "source_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.sourceId')),
    "target_id" VARCHAR(150) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.targetId')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_asset_lineage_source ON asset_lineage ("deleted", "tenant_id", "source_id");
CREATE INDEX IF NOT EXISTS ix_asset_lineage_target ON asset_lineage ("deleted", "tenant_id", "target_id");
