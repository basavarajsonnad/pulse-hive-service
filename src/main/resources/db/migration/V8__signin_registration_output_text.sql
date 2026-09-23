ALTER TABLE tenant_signin_config
    ALTER COLUMN registration_output TYPE text
    USING registration_output::text;
