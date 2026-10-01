CREATE TABLE IF NOT EXISTS Events (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    external_event BOOLEAN NOT NULL DEFAULT FALSE,
    delta_event BOOLEAN NOT NULL DEFAULT TRUE,
    location VARCHAR(100)
)