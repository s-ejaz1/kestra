CREATE TABLE IF NOT EXISTS audit_logs (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED,
    `user_email` VARCHAR(256) GENERATED ALWAYS AS (value ->> '$.userEmail') STORED NOT NULL,
    `resource` VARCHAR(50) GENERATED ALWAYS AS (value ->> '$.resource') STORED,
    `date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.date', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_deleted_tenant_date (`deleted`, `tenant_id`, `date`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
