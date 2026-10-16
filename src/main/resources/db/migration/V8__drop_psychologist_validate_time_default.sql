-- validate_time is set when a psychologist is validated; a default of now() marked unvalidated rows as validated.
ALTER TABLE psychologists ALTER COLUMN validate_time DROP DEFAULT;
