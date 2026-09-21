-- Core job_id is an opaque string (e.g. job_1a2b3c4d), not a UUID.

ALTER TABLE provisioning_item DROP CONSTRAINT provisioning_item_job_id_fkey;

ALTER TABLE provisioning_job
    ALTER COLUMN id DROP DEFAULT,
    ALTER COLUMN id TYPE text USING id::text;

ALTER TABLE provisioning_item
    ALTER COLUMN job_id TYPE text USING job_id::text;

ALTER TABLE provisioning_item
    ADD CONSTRAINT provisioning_item_job_id_fkey
        FOREIGN KEY (job_id) REFERENCES provisioning_job (id);
