CREATE TABLE program_seasons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    starts_on DATE NOT NULL,
    ends_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ends_on IS NULL OR ends_on >= starts_on)
);

CREATE UNIQUE INDEX program_seasons_one_current_idx
    ON program_seasons ((ends_on IS NULL))
    WHERE ends_on IS NULL;

CREATE TABLE program_season_settings (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    next_reset_date DATE NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO program_seasons (starts_on)
VALUES (
    date_trunc('year', CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Oslo')::date
);

INSERT INTO program_season_settings (singleton, next_reset_date)
VALUES (
    TRUE,
    (
        date_trunc('year', CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Oslo')
        + INTERVAL '1 year'
    )::date
);

CREATE TABLE activity_credits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    season_id UUID NOT NULL REFERENCES program_seasons(id),
    credit_type VARCHAR(40) NOT NULL
        CHECK (credit_type IN (
            'SLACK_WEEK',
            'DELTA_REGISTRATION',
            'GITHUB_COMMIT',
            'GITHUB_PULL_REQUEST',
            'SECURITY_EVENT_CONTRIBUTION'
        )),
    uniqueness_key VARCHAR(300) NOT NULL,
    source_reference VARCHAR(300) NOT NULL,
    points INTEGER NOT NULL CHECK (points > 0),
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (participant_id, credit_type, uniqueness_key),
    UNIQUE (id, participant_id, season_id)
);

CREATE INDEX activity_credits_season_participant_idx
    ON activity_credits (season_id, participant_id);

CREATE TABLE point_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    season_id UUID NOT NULL REFERENCES program_seasons(id),
    source_credit_id UUID,
    points_delta INTEGER NOT NULL CHECK (points_delta <> 0),
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    actor_nav_no_email VARCHAR(320) NOT NULL,
    score_before BIGINT NOT NULL,
    score_after BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (source_credit_id, participant_id, season_id)
        REFERENCES activity_credits (id, participant_id, season_id)
        ON DELETE CASCADE
);

CREATE INDEX point_adjustments_season_participant_idx
    ON point_adjustments (season_id, participant_id);

CREATE TABLE program_scoring_audit (
    id BIGSERIAL PRIMARY KEY,
    participant_id UUID REFERENCES program_participants(id) ON DELETE CASCADE,
    actor_nav_no_email VARCHAR(320),
    action VARCHAR(40) NOT NULL,
    affected_record_id UUID,
    reason TEXT,
    before_values JSONB NOT NULL,
    after_values JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX program_scoring_audit_actor_idx
    ON program_scoring_audit (actor_nav_no_email);

CREATE INDEX program_scoring_audit_participant_idx
    ON program_scoring_audit (participant_id);
