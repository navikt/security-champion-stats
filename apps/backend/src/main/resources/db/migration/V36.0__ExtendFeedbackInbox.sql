ALTER TABLE program_feedback DROP CONSTRAINT program_feedback_check;
ALTER TABLE program_feedback_history DROP CONSTRAINT program_feedback_history_action_check;
ALTER TABLE program_feedback_history ADD CHECK (action IN (
    'CLAIM', 'RELEASE', 'NOTE', 'INTERNAL_DISCUSSION', 'EXTERNAL_TICKET',
    'CONTACT_SUBMITTER', 'CLOSE', 'REOPEN', 'QUALIFY', 'DECLINE', 'TAKE_OVER', 'STATUS'
));
ALTER TABLE program_feedback_history
    ADD COLUMN previous_owner_email VARCHAR(320),
    ADD COLUMN status VARCHAR(20) CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED'));

CREATE TABLE program_feedback_reads (
    feedback_id UUID NOT NULL REFERENCES program_feedback(id) ON DELETE CASCADE,
    reader_email VARCHAR(320) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (feedback_id, reader_email)
);
CREATE INDEX program_feedback_reads_reader_idx ON program_feedback_reads(reader_email);
