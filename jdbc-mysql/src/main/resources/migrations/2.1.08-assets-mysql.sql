CREATE TABLE IF NOT EXISTS assets (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED,
    `id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `type` VARCHAR(512) GENERATED ALWAYS AS (value ->> '$.type') STORED,
    `display_name` VARCHAR(512) GENERATED ALWAYS AS (value ->> '$.displayName') STORED,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_deleted_tenant_id (`deleted`, `tenant_id`, `id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS asset_usages (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `asset_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.assetId') STORED NOT NULL,
    `date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.date', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_asset_date (`deleted`, `tenant_id`, `asset_id`, `date`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS asset_lineage (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `source_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.sourceId') STORED NOT NULL,
    `target_id` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.targetId') STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_source (`deleted`, `tenant_id`, `source_id`),
    INDEX ix_target (`deleted`, `tenant_id`, `target_id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
