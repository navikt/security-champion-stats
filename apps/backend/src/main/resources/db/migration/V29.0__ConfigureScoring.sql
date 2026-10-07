CREATE TABLE program_scoring_configuration (
    singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    version BIGINT NOT NULL DEFAULT 1
);
INSERT INTO program_scoring_configuration (singleton) VALUES (TRUE);

CREATE TABLE program_scoring_tiers (
    name VARCHAR(80) NOT NULL UNIQUE CHECK (length(trim(name)) > 0),
    points INTEGER PRIMARY KEY CHECK (points >= 0)
);
INSERT INTO program_scoring_tiers (name, points) VALUES
    ('Novice', 0), ('Apprentice', 100), ('Adept', 250), ('Expert', 500);

CREATE TABLE program_activity_points (
    credit_type VARCHAR(40) PRIMARY KEY,
    points INTEGER NOT NULL CHECK (points >= 0)
);
INSERT INTO program_activity_points (credit_type, points) VALUES
    ('SLACK_WEEK', 1), ('DELTA_REGISTRATION', 1), ('GITHUB_COMMIT', 1),
    ('GITHUB_PULL_REQUEST', 3), ('SECURITY_EVENT_CONTRIBUTION', 3);

ALTER TABLE activity_credits DROP CONSTRAINT activity_credits_points_check;
ALTER TABLE activity_credits ADD CHECK (points >= 0);
ALTER TABLE point_adjustments ADD COLUMN scoring_configuration_version BIGINT;
CREATE UNIQUE INDEX point_adjustments_repricing_idx
    ON point_adjustments (source_credit_id, scoring_configuration_version)
    WHERE scoring_configuration_version IS NOT NULL;
ALTER TABLE point_adjustments ADD CHECK (
    scoring_configuration_version IS NULL OR source_credit_id IS NOT NULL
);
