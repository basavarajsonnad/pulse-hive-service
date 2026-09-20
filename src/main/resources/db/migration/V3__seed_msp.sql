-- Id must match hive.msp.seed-id / HIVE_SEED_MSP_ID. Skip if a row already exists.
INSERT INTO msp (id, name, subdomain, status)
SELECT '00000000-0000-0000-0000-000000000001'::uuid,
       'CinchIT',
       'cinchit-hive',
       'active'
WHERE NOT EXISTS (SELECT 1 FROM msp);
