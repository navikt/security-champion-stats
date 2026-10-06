ALTER TABLE program_participants
    DROP CONSTRAINT program_participants_status_check;

ALTER TABLE program_participants
    ADD CONSTRAINT program_participants_status_check
        CHECK (status IN ('ACTIVE', 'DEACTIVATED', 'LEFT'));
