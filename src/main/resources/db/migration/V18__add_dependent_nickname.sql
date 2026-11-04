-- RF007: anonymous nickname for dependents. The column starts nullable so existing rows can be filled by
-- V19 (Java migration); V20 makes it NOT NULL.
ALTER TABLE dependents ADD COLUMN nickname VARCHAR(60) UNIQUE;

-- Every nickname a dependent ever had. The row with replaced_at IS NULL is the one in use.
CREATE TABLE dependent_nickname_history (
    id           BIGSERIAL PRIMARY KEY,
    dependent_id BIGINT NOT NULL REFERENCES dependents (user_id),
    nickname     VARCHAR(60) NOT NULL,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    replaced_at  TIMESTAMPTZ
);

CREATE INDEX idx_dependent_nickname_history_dependent ON dependent_nickname_history (dependent_id, assigned_at DESC);

-- at most one nickname in use per dependent
CREATE UNIQUE INDEX uq_dependent_nickname_history_current
    ON dependent_nickname_history (dependent_id) WHERE replaced_at IS NULL;
