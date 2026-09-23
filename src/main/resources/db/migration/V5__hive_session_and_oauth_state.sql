-- Opaque Hive login sessions (cookie value = id). Not MSP-RLS scoped:
-- looked up by cookie before SecurityContext has an MSP.
CREATE TABLE hive_session (
    id                        varchar(64) PRIMARY KEY,
    staff_id                  uuid NOT NULL REFERENCES staff (id),
    msp_id                    uuid NOT NULL REFERENCES msp (id),
    email                     varchar(320) NOT NULL,
    role                      varchar(64),
    access_token              text NOT NULL,
    id_token                  text NOT NULL,
    refresh_token             text NOT NULL,
    access_token_expires_at   timestamptz NOT NULL,
    created_at                timestamptz NOT NULL,
    expires_at                timestamptz NOT NULL,
    updated_at                timestamptz NOT NULL
);

CREATE INDEX ix_hive_session_expires_at ON hive_session (expires_at);
CREATE INDEX ix_hive_session_staff_id ON hive_session (staff_id);

-- Short-lived PKCE OAuth state (replaces Redis hive:oauth:state:*)
CREATE TABLE oauth_state (
    state          varchar(128) PRIMARY KEY,
    code_verifier  varchar(128) NOT NULL,
    expires_at     timestamptz NOT NULL,
    created_at     timestamptz NOT NULL
);

CREATE INDEX ix_oauth_state_expires_at ON oauth_state (expires_at);

-- App role is DML-only (see V1); grant when the role exists (local/dev).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hive_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON hive_session TO hive_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON oauth_state TO hive_app;
    END IF;
END $$;
