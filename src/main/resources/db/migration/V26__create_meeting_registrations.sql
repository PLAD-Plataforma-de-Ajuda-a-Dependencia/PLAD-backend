-- RF012/RF005: a dependent signs up for a meeting. One row per (meeting, dependent), so signing up twice is a
-- no-op at the database level too. The capacity is enforced by MeetingRegistrationService under a lock on the
-- meeting row, not here: a CHECK cannot count rows of another table.
-- Both foreign keys cascade (decision 9): deleting the dependent frees their seats, deleting the psychologist
-- removes their meetings and, with them, the registrations.

CREATE TABLE meeting_registrations (
    meeting_id    BIGINT      NOT NULL REFERENCES meetings (id)          ON DELETE CASCADE,
    dependent_id  BIGINT      NOT NULL REFERENCES dependents (user_id)   ON DELETE CASCADE,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (meeting_id, dependent_id)
);

-- "my meetings" and the data export look rows up by dependent; the primary key already serves the meeting side
CREATE INDEX idx_meeting_registrations_dependent ON meeting_registrations (dependent_id);
