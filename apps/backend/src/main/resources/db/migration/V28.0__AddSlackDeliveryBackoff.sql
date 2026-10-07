ALTER TABLE slack_membership_announcements
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
