-- V19 gave a nickname to every existing dependent; from here on the column is mandatory.
ALTER TABLE dependents ALTER COLUMN nickname SET NOT NULL;
