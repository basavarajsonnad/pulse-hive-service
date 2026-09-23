-- Hive staff row created on first Cognito login (find-or-create by email).
-- Role is optional: Cognito owns access control; Hive stores the local row for session/FK.

CREATE TABLE staff (
    id          uuid PRIMARY KEY,
    email       varchar(320) NOT NULL,
    role        varchar(64),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT uq_staff_email UNIQUE (email)
);
