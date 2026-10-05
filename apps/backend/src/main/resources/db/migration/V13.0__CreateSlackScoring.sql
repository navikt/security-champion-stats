CREATE TABLE slack_account_mappings (
    slack_user_id VARCHAR(100) PRIMARY KEY,
    participant_id UUID NOT NULL UNIQUE REFERENCES program_participants(id) ON DELETE CASCADE,
    created_by_nav_no_email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE slack_unmapped_authors (
    slack_user_id VARCHAR(100) PRIMARY KEY,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE slack_scoring_sync_state (
    channel_id VARCHAR(100) PRIMARY KEY,
    last_synced_at TIMESTAMPTZ NOT NULL
);
