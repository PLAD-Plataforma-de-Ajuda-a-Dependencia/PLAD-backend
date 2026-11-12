-- LGPD art. 8, § 5 and art. 18, IX: a consent can be revoked (today only the health one, DELETE /me/consents/health).
-- The row is not deleted: revoked_at marks it and it stays as proof of what was accepted and when it was withdrawn.
ALTER TABLE consents ADD COLUMN revoked_at TIMESTAMPTZ;

-- One ACTIVE acceptance per user, document and version. Accepting the same version again after revoking it adds a new
-- row next to the revoked one, which the old full unique index (V21) would refuse.
DROP INDEX uq_consents_user_document_version;
CREATE UNIQUE INDEX uq_consents_user_document_version_active ON consents (user_id, document, version)
    WHERE revoked_at IS NULL;
