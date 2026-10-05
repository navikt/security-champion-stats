ALTER TABLE program_delta_event_mappings
    ADD COLUMN delta_category_id INTEGER
    CHECK (delta_category_id IS NULL OR delta_category_id > 0);
