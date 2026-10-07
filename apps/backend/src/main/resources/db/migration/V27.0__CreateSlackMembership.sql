CREATE TABLE slack_membership_baselines (
    usergroup_id VARCHAR(80) PRIMARY KEY,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE slack_membership_snapshot (
    usergroup_id VARCHAR(80) NOT NULL REFERENCES slack_membership_baselines(usergroup_id),
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    slack_user_id VARCHAR(80) NOT NULL,
    PRIMARY KEY (usergroup_id, participant_id),
    UNIQUE (usergroup_id, slack_user_id)
);

CREATE TABLE slack_membership_announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usergroup_id VARCHAR(80) NOT NULL REFERENCES slack_membership_baselines(usergroup_id),
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    slack_user_id VARCHAR(80) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('WELCOME', 'REMOVAL')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'SUPPRESSED', 'CANCELLED', 'UNCERTAIN')),
    message_ts VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX slack_membership_announcements_pending_idx
    ON slack_membership_announcements (usergroup_id, created_at)
    WHERE status IN ('PENDING', 'SENDING', 'UNCERTAIN');
