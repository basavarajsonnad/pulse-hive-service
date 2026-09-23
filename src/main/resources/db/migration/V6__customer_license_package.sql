-- License package chosen on create (Basic / Intermediate / Advanced).
-- Existing rows default to basic.

ALTER TABLE customer
    ADD COLUMN license_package text NOT NULL DEFAULT 'basic'
        CHECK (license_package IN ('basic', 'intermediate', 'advanced'));
