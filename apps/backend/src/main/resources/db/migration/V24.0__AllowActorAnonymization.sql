ALTER TABLE point_adjustments ALTER COLUMN actor_nav_no_email DROP NOT NULL;
ALTER TABLE slack_account_mappings ALTER COLUMN created_by_nav_no_email DROP NOT NULL;
ALTER TABLE program_delta_event_mappings ALTER COLUMN created_by_nav_no_email DROP NOT NULL;
ALTER TABLE delta_eligible_categories ALTER COLUMN created_by_nav_no_email DROP NOT NULL;
