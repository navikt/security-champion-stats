ALTER TABLE program_participants
    ADD COLUMN deactivation_reason VARCHAR(40)
        CHECK (deactivation_reason IN ('SLACK_CHANNEL_DEPARTURE'));

ALTER TABLE program_participants
    ADD CONSTRAINT program_participants_deactivation_reason_status_check
        CHECK (deactivation_reason IS NULL OR status = 'DEACTIVATED');

CREATE TABLE slack_channel_participation (
    participant_id UUID PRIMARY KEY REFERENCES program_participants(id) ON DELETE CASCADE,
    channel_id VARCHAR(80) NOT NULL,
    slack_user_id VARCHAR(80),
    present BOOLEAN,
    absent_since TIMESTAMPTZ,
    checked_at TIMESTAMPTZ NOT NULL,
    CHECK ((slack_user_id IS NULL) = (present IS NULL)),
    CHECK (absent_since IS NULL OR present = FALSE)
);

CREATE TABLE slack_channel_departure_notices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    channel_id VARCHAR(80) NOT NULL,
    slack_user_id VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'CANCELLED', 'UNCERTAIN')),
    message_ts VARCHAR(80),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX slack_channel_departure_notices_participant_idx
    ON slack_channel_departure_notices (participant_id, created_at DESC);

CREATE TABLE slack_channel_participation_status (
    channel_id VARCHAR(80) PRIMARY KEY,
    last_attempt_at TIMESTAMPTZ NOT NULL,
    last_success_at TIMESTAMPTZ,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('RUNNING', 'SUCCEEDED', 'PARTIAL_FAILURE', 'FAILED')),
    failure_summary VARCHAR(200)
);
