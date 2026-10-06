CREATE TABLE IF NOT EXISTS iam_users (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "email" VARCHAR(256) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.email')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_iam_users_deleted_tenant_email ON iam_users ("deleted", "tenant_id", "email");

CREATE TABLE IF NOT EXISTS iam_roles (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "name" VARCHAR(250) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.name')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE TABLE IF NOT EXISTS iam_bindings (
    "key" VARCHAR(250) NOT NULL PRIMARY KEY,
    "value" TEXT NOT NULL,
    "tenant_id" VARCHAR(250) GENERATED ALWAYS AS (JQ_STRING("value", '.tenantId')),
    "id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.id')),
    "user_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.userId')),
    "role_id" VARCHAR(100) NOT NULL GENERATED ALWAYS AS (JQ_STRING("value", '.roleId')),
    "deleted" BOOL NOT NULL GENERATED ALWAYS AS (JQ_BOOLEAN("value", '.deleted'))
);

CREATE INDEX IF NOT EXISTS ix_iam_bindings_deleted_tenant_user ON iam_bindings ("deleted", "tenant_id", "user_id");
