-- Seeded CinchIT MSP until JWT supplies msp_id. Must match HIVE_SEED_MSP_ID.
INSERT INTO msp (id, name, subdomain, status)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CinchIT',
    'cinchit-hive',
    'active'
);
