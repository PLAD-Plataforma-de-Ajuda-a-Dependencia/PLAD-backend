-- every triage question is optional; a triage with no answers is simply not saved
UPDATE questions_triages
SET required = false;

ALTER TABLE questions_triages
    ALTER COLUMN required SET DEFAULT false;
