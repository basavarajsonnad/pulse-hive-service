-- Background poller and login need to list/create MSP rows without a request
-- app.current_msp. Scoped DML on other tables still uses p_msp / p_* policies.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'hive_app') THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_policy
            WHERE polrelid = 'msp'::regclass AND polname = 'p_msp_select_directory'
        ) THEN
            CREATE POLICY p_msp_select_directory ON msp
                FOR SELECT
                TO hive_app
                USING (true);
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_policy
            WHERE polrelid = 'msp'::regclass AND polname = 'p_msp_insert_bootstrap'
        ) THEN
            CREATE POLICY p_msp_insert_bootstrap ON msp
                FOR INSERT
                TO hive_app
                WITH CHECK (true);
        END IF;
    END IF;
END $$;
