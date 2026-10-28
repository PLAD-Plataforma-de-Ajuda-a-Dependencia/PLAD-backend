ALTER TABLE questions_triages
    ADD COLUMN code VARCHAR(50);

-- the V5 seeds are identified by display_order; ids are BIGSERIAL and differ between environments
UPDATE questions_triages SET code = 'AGE_RANGE' WHERE display_order = 1;
UPDATE questions_triages SET code = 'REASON' WHERE display_order = 2;
UPDATE questions_triages SET code = 'USE_FREQUENCY_30D' WHERE display_order = 3;
UPDATE questions_triages SET code = 'CONTROL_DIFFICULTY' WHERE display_order = 4;
UPDATE questions_triages SET code = 'SUPPORT_NETWORK' WHERE display_order = 5;
UPDATE questions_triages SET code = 'PREVIOUS_TREATMENT' WHERE display_order = 6;
UPDATE questions_triages SET code = 'GROUP_EXPERIENCE' WHERE display_order = 7;
UPDATE questions_triages SET code = 'PREFERRED_FORMAT' WHERE display_order = 8;
UPDATE questions_triages SET code = 'FOLLOWUP_STYLE' WHERE display_order = 9;
UPDATE questions_triages SET code = 'NOTES_FOR_TEAM' WHERE display_order = 10;

ALTER TABLE questions_triages
    ALTER COLUMN code SET NOT NULL,
    ADD CONSTRAINT uq_questions_triages_code UNIQUE (code);
