CREATE TABLE IF NOT EXISTS test_suites (
    `key` VARCHAR(768) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `flow_id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.flowId') STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_deleted_tenant_namespace_id (`deleted`, `tenant_id`, `namespace`, `id`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS test_suite_runs (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED NOT NULL,
    `test_suite_id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.testSuiteId') STORED NOT NULL,
    `start_date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.startDate', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_suite_start_date (`deleted`, `tenant_id`, `namespace`, `test_suite_id`, `start_date`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
