CREATE TABLE IF NOT EXISTS policies (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `enforcement` VARCHAR(20) GENERATED ALWAYS AS (value ->> '$.enforcement') STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_deleted_tenant_id (`deleted`, `tenant_id`, `id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
