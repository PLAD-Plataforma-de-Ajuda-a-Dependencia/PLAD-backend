-- existing values were written as naive local time; read them as America/Sao_Paulo
ALTER TABLE users             ALTER COLUMN created_at    TYPE TIMESTAMPTZ USING created_at    AT TIME ZONE 'America/Sao_Paulo';
ALTER TABLE psychologists     ALTER COLUMN validate_time TYPE TIMESTAMPTZ USING validate_time AT TIME ZONE 'America/Sao_Paulo';
ALTER TABLE dependents        ALTER COLUMN created_at    TYPE TIMESTAMPTZ USING created_at    AT TIME ZONE 'America/Sao_Paulo';
ALTER TABLE triages           ALTER COLUMN created_at    TYPE TIMESTAMPTZ USING created_at    AT TIME ZONE 'America/Sao_Paulo';
ALTER TABLE questions_triages ALTER COLUMN created_at    TYPE TIMESTAMPTZ USING created_at    AT TIME ZONE 'America/Sao_Paulo';
