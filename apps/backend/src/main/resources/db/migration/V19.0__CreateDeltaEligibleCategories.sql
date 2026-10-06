CREATE TABLE delta_eligible_categories (
    category_id INTEGER PRIMARY KEY CHECK (category_id > 0),
    category_name VARCHAR(200) NOT NULL CHECK (length(trim(category_name)) > 0),
    created_by_nav_no_email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
