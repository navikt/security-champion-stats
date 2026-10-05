CREATE TABLE program_delta_event_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_event_name VARCHAR(200) NOT NULL CHECK (length(trim(program_event_name)) > 0),
    delta_event_uuid UUID NOT NULL UNIQUE,
    created_by_nav_no_email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
