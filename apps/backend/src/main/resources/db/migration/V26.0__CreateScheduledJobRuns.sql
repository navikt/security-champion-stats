CREATE TABLE scheduled_job_runs (
    job_name VARCHAR(120) PRIMARY KEY,
    last_started_at TIMESTAMPTZ NOT NULL
);
