CREATE TABLE delta_scoring_sync_status (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton = TRUE),
    last_attempt_at TIMESTAMPTZ NOT NULL,
    last_success_at TIMESTAMPTZ,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('RUNNING', 'SUCCEEDED', 'PARTIAL_FAILURE', 'FAILED')),
    events_scanned INTEGER NOT NULL DEFAULT 0,
    credits_awarded INTEGER NOT NULL DEFAULT 0,
    duplicate_credits INTEGER NOT NULL DEFAULT 0,
    unmatched_registrations INTEGER NOT NULL DEFAULT 0,
    failed_events INTEGER NOT NULL DEFAULT 0,
    failure_summary VARCHAR(200)
);
