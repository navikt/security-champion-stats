DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM Events
        GROUP BY lower(btrim(name)), start_date, lower(btrim(coalesce(location, '')))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate program events exist. Resolve duplicate name/start/location groups before migration; '
            'no events have been deleted.';
    END IF;
END $$;

CREATE UNIQUE INDEX events_name_start_location_unique
    ON Events (lower(btrim(name)), start_date, lower(btrim(coalesce(location, ''))));
