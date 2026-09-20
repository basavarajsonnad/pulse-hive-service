CREATE TABLE msp_tenants (
    job_id          VARCHAR(64)  PRIMARY KEY,
    msp_id          UUID         NOT NULL REFERENCES msp (id),
    customer_name   VARCHAR(25)  NOT NULL,
    tenant_name     VARCHAR(64)  NULL,
    status          VARCHAR(20)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_msp_tenants_status
        CHECK (status IN ('in_progress', 'complete', 'failed'))
);

CREATE INDEX idx_msp_tenants_msp_id ON msp_tenants (msp_id);
CREATE INDEX idx_msp_tenants_status ON msp_tenants (status);
