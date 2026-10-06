CREATE TABLE IF NOT EXISTS iam_users (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "email" VARCHAR(256) NOT NULL GENERATED ALWAYS AS (value ->> 'email') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_iam_users_deleted_tenant_email ON iam_users ("deleted", "tenant_id", "email");

CREATE TABLE IF NOT EXISTS iam_roles (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "name" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'name') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE TABLE IF NOT EXISTS iam_bindings (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" JSONB NOT NULL,
    "tenant_id" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (value ->> 'tenantId') STORED,
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'id') STORED,
    "user_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'userId') STORED,
    "role_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (value ->> 'roleId') STORED,
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (CAST(value ->> 'deleted' AS BOOL)) STORED
);

CREATE INDEX IF NOT EXISTS ix_iam_bindings_deleted_tenant_user ON iam_bindings ("deleted", "tenant_id", "user_id");
