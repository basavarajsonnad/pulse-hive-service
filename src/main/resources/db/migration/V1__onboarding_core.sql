-- Customer onboarding under an MSP (minimal cut).
-- Every customer-scoped table carries msp_id and is protected by row-level
-- security (see the RLS block at the end).
--
-- RLS enforcement requires two runtime conditions the schema cannot guarantee:
--   1. The application connects as a NON-superuser, NON-owner role. Superusers
--      and BYPASSRLS roles ignore RLS even with FORCE; point DB_USERNAME at a
--      plain role (e.g. hive_app) with only DML grants.
--   2. Every transaction sets app.current_msp via
--      SELECT set_config('app.current_msp', '<uuid>', true);
--      RLS is default-deny: without it, reads return zero rows and writes fail
--      the WITH CHECK. Wire this in the request/transaction boundary.

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
    status       text NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed', 'failed')),
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

-- ---------- Row-level security ----------
-- FORCE so the table owner (the migration/runtime role, if they coincide) is
-- also subject to policies. app.current_msp is read with missing_ok = true so an
-- unset GUC yields NULL -> policy false -> default deny.

-- msp: a scoped session sees only its own row (predicate on id, not msp_id)
ALTER TABLE msp ENABLE ROW LEVEL SECURITY;
ALTER TABLE msp FORCE  ROW LEVEL SECURITY;
CREATE POLICY p_msp ON msp
    USING      (id = current_setting('app.current_msp', true)::uuid)
    WITH CHECK (id = current_setting('app.current_msp', true)::uuid);

-- every customer-scoped table: identical predicate on msp_id
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['provisioning_job', 'customer', 'provisioning_item', 'tenant_signin_config'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE  ROW LEVEL SECURITY', t);
        EXECUTE format($f$CREATE POLICY p_%1$s ON %1$I
            USING      (msp_id = current_setting('app.current_msp', true)::uuid)
            WITH CHECK (msp_id = current_setting('app.current_msp', true)::uuid)$f$, t);
    END LOOP;
END $$;