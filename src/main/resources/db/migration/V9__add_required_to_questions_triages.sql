ALTER TABLE questions_triages
    ADD COLUMN required BOOLEAN NOT NULL DEFAULT true;

-- the free text question is the only optional one
UPDATE questions_triages
SET required = false
WHERE display_order = 10;
