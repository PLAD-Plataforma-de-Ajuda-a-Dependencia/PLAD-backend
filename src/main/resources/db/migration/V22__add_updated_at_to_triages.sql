-- when the answers last changed: set on insert and again on every PUT /triage. Existing rows start at created_at.
ALTER TABLE triages ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE triages SET updated_at = created_at;
ALTER TABLE triages ALTER COLUMN updated_at SET NOT NULL;
