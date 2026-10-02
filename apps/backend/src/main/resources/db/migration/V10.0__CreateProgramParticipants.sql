CREATE TABLE program_participants (
    id UUID PRIMARY KEY,
    nav_no_email VARCHAR(320) NOT NULL UNIQUE,
    nav_ident VARCHAR(70),
    email VARCHAR(320) NOT NULL,
    fullname VARCHAR(100) NOT NULL,
    teams TEXT[] NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX program_participants_nav_ident_email_idx
    ON program_participants (nav_ident, email);
