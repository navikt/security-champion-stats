ALTER TABLE activity_credits
    ADD COLUMN source_name TEXT,
    ADD COLUMN source_url TEXT,
    ADD COLUMN source_occurred_at TIMESTAMPTZ;

UPDATE activity_credits AS credit
SET source_name = claim.name,
    source_url = claim.links ->> 0,
    source_occurred_at = claim.start_date
FROM event_contribution_claims AS claim
WHERE credit.credit_type = 'SECURITY_EVENT_CONTRIBUTION'
    AND credit.source_reference = 'event-claim:' || claim.id;

UPDATE activity_credits AS credit
SET source_name = event.name,
    source_url = event.link,
    source_occurred_at = event.start_date
FROM Events AS event
WHERE credit.source_name IS NULL
    AND credit.credit_type IN ('SECURITY_EVENT_CONTRIBUTION', 'DELTA_REGISTRATION')
    AND credit.source_reference = event.id::text;

UPDATE activity_credits AS credit
SET source_name = mapping.program_event_name
FROM program_delta_event_mappings AS mapping
WHERE credit.credit_type = 'DELTA_REGISTRATION'
    AND credit.source_name IS NULL
    AND credit.source_reference = mapping.delta_event_uuid::text;

UPDATE activity_credits
SET source_url = 'https://delta.nav.no/event/' || source_reference
WHERE credit_type = 'DELTA_REGISTRATION'
    AND source_reference ~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$';

UPDATE activity_credits
SET source_name = 'navikt/security-playbook #' || split_part(source_reference, ':', 3),
    source_url = 'https://github.com/navikt/security-playbook/pull/' || split_part(source_reference, ':', 3),
    source_occurred_at = activity_at
WHERE credit_type = 'GITHUB_PULL_REQUEST'
    AND source_reference ~ '^navikt/security-playbook:pr:[0-9]+$';

UPDATE activity_credits
SET source_name = 'navikt/security-playbook commit ' || left(split_part(source_reference, ':', 3), 7),
    source_url = 'https://github.com/navikt/security-playbook/commit/' || split_part(source_reference, ':', 3),
    source_occurred_at = activity_at
WHERE credit_type = 'GITHUB_COMMIT'
    AND source_reference ~ '^navikt/security-playbook:commit:[0-9a-fA-F]{40}$';
