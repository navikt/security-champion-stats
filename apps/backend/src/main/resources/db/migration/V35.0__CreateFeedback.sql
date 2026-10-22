ALTER TABLE activity_credits DROP CONSTRAINT activity_credits_credit_type_check;
ALTER TABLE activity_credits ADD CHECK (credit_type IN (
    'SLACK_WEEK', 'DELTA_REGISTRATION', 'GITHUB_COMMIT', 'GITHUB_PULL_REQUEST',
    'SECURITY_EVENT_CONTRIBUTION', 'FEEDBACK'
));
INSERT INTO program_activity_points (credit_type, points) VALUES ('FEEDBACK', 1);
UPDATE program_scoring_configuration SET version = version + 1 WHERE singleton = TRUE;

CREATE TABLE program_feedback (
    id UUID PRIMARY KEY,
    submitter_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    season_id UUID NOT NULL REFERENCES program_seasons(id),
    title VARCHAR(200) NOT NULL CHECK (length(btrim(title)) > 0),
    description TEXT NOT NULL CHECK (length(btrim(description)) BETWEEN 1 AND 5000),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    owner_email VARCHAR(320),
    score_decision VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (score_decision IN ('PENDING', 'QUALIFIED', 'NOT_QUALIFIED')),
    credit_id UUID UNIQUE REFERENCES activity_credits(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK ((status = 'IN_PROGRESS') = (owner_email IS NOT NULL))
);
CREATE INDEX program_feedback_created_idx ON program_feedback(created_at DESC, id DESC);
CREATE INDEX program_feedback_submitter_idx ON program_feedback(submitter_id);

CREATE TABLE program_feedback_history (
    id BIGSERIAL PRIMARY KEY,
    feedback_id UUID NOT NULL REFERENCES program_feedback(id) ON DELETE CASCADE,
    actor_email VARCHAR(320),
    action VARCHAR(30) NOT NULL CHECK (action IN (
        'CLAIM', 'RELEASE', 'NOTE', 'INTERNAL_DISCUSSION', 'EXTERNAL_TICKET',
        'CONTACT_SUBMITTER', 'CLOSE', 'REOPEN', 'QUALIFY', 'DECLINE'
    )),
    note TEXT NOT NULL CHECK (length(note) <= 3000),
    ticket_url VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX program_feedback_history_feedback_idx ON program_feedback_history(feedback_id, id);
