CREATE TABLE github_account_mappings (
    github_account_id BIGINT PRIMARY KEY CHECK (github_account_id > 0),
    github_login VARCHAR(100) NOT NULL,
    participant_id UUID NOT NULL UNIQUE REFERENCES program_participants(id) ON DELETE CASCADE,
    verified_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE activity_credits ADD COLUMN activity_at TIMESTAMPTZ;

CREATE UNIQUE INDEX activity_credits_github_contribution_idx
    ON activity_credits (credit_type, uniqueness_key)
    WHERE credit_type IN ('GITHUB_COMMIT', 'GITHUB_PULL_REQUEST');

CREATE TABLE github_scoring_sync_status (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    last_attempt_at TIMESTAMPTZ NOT NULL,
    last_success_at TIMESTAMPTZ,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    contributions_scanned INTEGER NOT NULL DEFAULT 0,
    credits_awarded INTEGER NOT NULL DEFAULT 0,
    duplicate_credits INTEGER NOT NULL DEFAULT 0,
    unmapped_authors INTEGER NOT NULL DEFAULT 0,
    failure_summary VARCHAR(200)
);
