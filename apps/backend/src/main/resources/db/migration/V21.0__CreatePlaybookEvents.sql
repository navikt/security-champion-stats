CREATE TABLE playbook_events (
    id TEXT PRIMARY KEY CHECK (id LIKE 'playbook:%' OR id LIKE 'external:%'),
    title TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL CHECK (end_date >= start_date),
    audience TEXT NOT NULL,
    url TEXT NOT NULL
);
