-- Core's job_id is an opaque string. Hive PK stays uuid (V1).

ALTER TABLE provisioning_job
    ADD COLUMN core_job_reference text NOT NULL;

CREATE UNIQUE INDEX uq_provisioning_job_core_job_reference
    ON provisioning_job (core_job_reference);
