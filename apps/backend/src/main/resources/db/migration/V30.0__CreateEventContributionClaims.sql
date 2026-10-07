ALTER TABLE activity_credits ADD COLUMN revoked_at TIMESTAMPTZ;
ALTER TABLE Events ALTER COLUMN link TYPE VARCHAR(1000);

CREATE TABLE event_contribution_claims (
    id UUID PRIMARY KEY,
    submitter_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    season_id UUID NOT NULL REFERENCES program_seasons(id),
    event_id UUID REFERENCES Events(id),
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL CHECK (end_date > start_date),
    location VARCHAR(100) NOT NULL,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN ('MEETUP', 'WORKSHOP')),
    external_event BOOLEAN NOT NULL,
    links JSONB NOT NULL,
    invitation_evidence TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX event_claim_identity_idx ON event_contribution_claims
    (lower(btrim(name)), start_date, lower(btrim(location)));
CREATE UNIQUE INDEX event_claim_event_idx ON event_contribution_claims(event_id) WHERE event_id IS NOT NULL;

CREATE TABLE event_claim_contributors (
    claim_id UUID NOT NULL REFERENCES event_contribution_claims(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    contribution TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED')),
    credit_id UUID REFERENCES activity_credits(id) ON DELETE SET NULL,
    PRIMARY KEY (claim_id, participant_id)
);

CREATE TABLE event_claim_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id UUID NOT NULL REFERENCES event_contribution_claims(id) ON DELETE CASCADE,
    participant_id UUID NOT NULL REFERENCES program_participants(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES program_participants(id) ON DELETE SET NULL,
    actor_nav_no_email VARCHAR(320),
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED', 'REVOKED')),
    reason TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX event_claim_reviews_claim_created_idx
    ON event_claim_reviews (claim_id, created_at, id);
