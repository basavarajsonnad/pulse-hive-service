-- Seeded CinchIT MSP until JWT supplies msp_id. Must match HIVE_SEED_MSP_ID.
INSERT INTO msp (id, name, subdomain, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CinchIT',
    'cinchit-hive',
    'active'
);

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'hive_app') THEN
        GRANT USAGE ON SCHEMA public TO hive_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO hive_app;
    END IF;
END $$;
