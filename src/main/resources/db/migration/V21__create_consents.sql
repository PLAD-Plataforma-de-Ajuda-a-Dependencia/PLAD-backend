-- LGPD art. 11 consent record: which document and version a user accepted, when, and from which IP.
-- One row per (user, document, version); accepting a newer version adds a row, older ones stay as proof.
-- Existing accounts get no rows on purpose: consent is never fabricated, they accept through POST /me/consents.
CREATE TABLE consents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    document VARCHAR(30) NOT NULL,
    version VARCHAR(20) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip VARCHAR(45)
);
CREATE UNIQUE INDEX uq_consents_user_document_version ON consents (user_id, document, version);
CREATE INDEX idx_consents_user ON consents (user_id);
