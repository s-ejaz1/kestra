CREATE TABLE IF NOT EXISTS secret_metadata (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `name` VARCHAR(350) GENERATED ALWAYS AS (value ->> '$.name') STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_deleted_tenant_namespace_name (`deleted`, `tenant_id`, `namespace`, `name`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
