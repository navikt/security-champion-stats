CREATE TABLE program_audit_rollout (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO program_audit_rollout (singleton) VALUES (TRUE);

CREATE TABLE program_audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    action VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'PARTIAL')),
    actor_nav_no_email VARCHAR(320),
    target_participant_id UUID REFERENCES program_participants(id) ON DELETE CASCADE,
    correlation_id UUID,
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX program_audit_events_created_at_idx
    ON program_audit_events (created_at DESC);

CREATE INDEX program_audit_events_target_created_at_idx
    ON program_audit_events (target_participant_id, created_at DESC)
    WHERE target_participant_id IS NOT NULL;

CREATE INDEX program_audit_events_actor_idx
    ON program_audit_events (actor_nav_no_email)
    WHERE actor_nav_no_email IS NOT NULL;

CREATE INDEX program_audit_events_correlation_idx
    ON program_audit_events (correlation_id)
    WHERE correlation_id IS NOT NULL;

ALTER TABLE activity_credits
    ADD COLUMN audit_correlation_id UUID;

CREATE INDEX activity_credits_audit_correlation_idx
    ON activity_credits (audit_correlation_id)
    WHERE audit_correlation_id IS NOT NULL;
