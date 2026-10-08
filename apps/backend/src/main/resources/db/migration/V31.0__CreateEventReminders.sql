CREATE TABLE event_reminder_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    slack_user_id VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('SENDING', 'SENT', 'FAILED', 'UNCERTAIN')),
    message_ts VARCHAR(80),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (event_id, participant_id)
);
