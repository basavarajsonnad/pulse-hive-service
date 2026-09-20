-- Customer onboarding under an MSP (minimal cut).
-- Every customer-scoped table carries msp_id so row-level security can be added
-- in a later migration without a schema change. RLS policies are intentionally
-- deferred (they require a non-superuser app role + per-transaction set_config).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------- msp (platform root) ----------
CREATE TABLE msp (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     text NOT NULL,
    subdomain                text NOT NULL,
    status                   text NOT NULL DEFAULT 'active'
                                 CHECK (status IN ('active', 'suspended')),
    identity_pool_reference  text,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_msp_subdomain UNIQUE (subdomain)
);

-- ---------- provisioning_job ----------
CREATE TABLE provisioning_job (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    msp_id         uuid NOT NULL REFERENCES msp (id),
    type           text NOT NULL CHECK (type IN ('single', 'bulk')),
    status         text NOT NULL DEFAULT 'queued'
                       CHECK (status IN ('queued', 'running', 'completed', 'completed_with_errors', 'failed')),
    total_count    integer NOT NULL DEFAULT 0,
    success_count  integer NOT NULL DEFAULT 0,
    failure_count  integer NOT NULL DEFAULT 0,
    started_at     timestamptz,
    finished_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_provisioning_job_msp ON provisioning_job (msp_id);

-- ---------- customer (onboarding target; hangs directly off msp) ----------
CREATE TABLE customer (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    msp_id       uuid NOT NULL REFERENCES msp (id),
    name         text NOT NULL,
    tenant_name  text,            -- generated during the job run, not user input
    status       text NOT NULL DEFAULT 'queued'
                     CHECK (status IN ('queued', 'running', 'completed', 'completed_with_errors', 'failed')),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_customer_name UNIQUE (msp_id, name)
);
CREATE UNIQUE INDEX uq_customer_tenant_name
    ON customer (msp_id, tenant_name) WHERE tenant_name IS NOT NULL;

-- ---------- provisioning_item (one input row of a job) ----------
CREATE TABLE provisioning_item (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    msp_id         uuid NOT NULL REFERENCES msp (id),
    job_id         uuid NOT NULL REFERENCES provisioning_job (id),
    row_number     integer,
    customer_name  text NOT NULL,
    status         text NOT NULL DEFAULT 'queued'
                       CHECK (status IN ('queued', 'running', 'completed', 'completed_with_errors', 'failed')),
    customer_id    uuid REFERENCES customer (id),   -- set when the row succeeds
    error          text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_provisioning_item_job ON provisioning_item (job_id);

-- ---------- tenant_signin_config ----------
CREATE TABLE tenant_signin_config (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    msp_id               uuid NOT NULL REFERENCES msp (id),
    customer_id          uuid NOT NULL REFERENCES customer (id),
    protocol             text NOT NULL CHECK (protocol IN ('saml', 'oidc')),
    provider_input       jsonb NOT NULL,   -- customer-supplied: metadata_url, groups_claim, (oidc client_id, secret_ref), ...
    registration_output  jsonb,            -- job-returned: callback_url, (saml sp_entity_id), ...
    status               text NOT NULL DEFAULT 'active'
                             CHECK (status IN ('active', 'disabled')),
    registered_at        timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_tenant_signin_customer UNIQUE (customer_id)
);
