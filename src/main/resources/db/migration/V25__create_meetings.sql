-- RF012: meetings hosted by a psychologist. The rules the API enforces (15-180 min, capacity 2-30) are also CHECKs,
-- and the same psychologist cannot have two scheduled meetings whose intervals cross: tstzrange is [) so a meeting
-- that ends at 19:00 and one that starts at 19:00 touch without overlapping. Cancelled and ended ones free the slot.
-- btree_gist lets the exclusion constraint compare psychologist_id with "=" next to the range "&&".
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE meetings (
    id BIGSERIAL PRIMARY KEY,
    psychologist_id BIGINT NOT NULL REFERENCES psychologists (user_id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'scheduled',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    CONSTRAINT meetings_status_check CHECK (status IN ('scheduled', 'cancelled', 'ended')),
    CONSTRAINT meetings_duration_check CHECK (
        ends_at - starts_at BETWEEN INTERVAL '15 minutes' AND INTERVAL '180 minutes'),
    CONSTRAINT meetings_capacity_check CHECK (capacity BETWEEN 2 AND 30),
    CONSTRAINT meetings_no_overlap EXCLUDE USING gist (
        psychologist_id WITH =,
        tstzrange(starts_at, ends_at) WITH &&
    ) WHERE (status = 'scheduled')
);
CREATE INDEX idx_meetings_starts_at ON meetings (starts_at);
