CREATE TABLE program_participant_audit (
    id BIGSERIAL PRIMARY KEY,
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    actor_nav_no_email VARCHAR(320),
    action VARCHAR(40) NOT NULL,
    before_values JSONB NOT NULL,
    after_values JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX program_participant_audit_actor_idx
    ON program_participant_audit (actor_nav_no_email);

CREATE INDEX program_participant_audit_participant_idx
    ON program_participant_audit (participant_id);
