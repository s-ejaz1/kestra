CREATE TABLE IF NOT EXISTS cases (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `namespace` VARCHAR(150) GENERATED ALWAYS AS (value ->> '$.namespace') STORED,
    `id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.id') STORED NOT NULL,
    `title` VARCHAR(512) GENERATED ALWAYS AS (value ->> '$.title') STORED NOT NULL,
    `status` VARCHAR(20) GENERATED ALWAYS AS (value ->> '$.status') STORED NOT NULL,
    `severity` VARCHAR(20) GENERATED ALWAYS AS (value ->> '$.severity') STORED NOT NULL,
    `flow_id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.flowId') STORED,
    `auto_link` BOOL GENERATED ALWAYS AS (value ->> '$.autoLink' = 'true') STORED NOT NULL,
    `updated` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.updated', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_deleted_tenant_updated (`deleted`, `tenant_id`, `updated`),
    INDEX ix_auto_link (`deleted`, `tenant_id`, `namespace`, `flow_id`, `auto_link`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS case_activities (
    `key` VARCHAR(250) NOT NULL PRIMARY KEY,
    `value` JSON NOT NULL,
    `tenant_id` VARCHAR(250) GENERATED ALWAYS AS (value ->> '$.tenantId') STORED NOT NULL,
    `case_id` VARCHAR(100) GENERATED ALWAYS AS (value ->> '$.caseId') STORED NOT NULL,
    `date` DATETIME(6) GENERATED ALWAYS AS (STR_TO_DATE(value ->> '$.date', '%Y-%m-%dT%H:%i:%s.%fZ')) STORED NOT NULL,
    `deleted` BOOL GENERATED ALWAYS AS (value ->> '$.deleted' = 'true') STORED NOT NULL,
    INDEX ix_case_date (`deleted`, `tenant_id`, `case_id`, `date`)
) ENGINE INNODB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
