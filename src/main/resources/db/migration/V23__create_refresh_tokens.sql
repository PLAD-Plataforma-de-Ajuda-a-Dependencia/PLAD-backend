-- Refresh tokens (opaque, 32 random bytes): only the SHA-256 hex of the token is stored, never the token itself.
-- A used token is kept with revoked_at set so that presenting it again is recognised as reuse (theft signal);
-- RefreshTokenCleanupJob deletes expired or long-revoked rows.
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- Bumped to invalidate every access token of a user at once (e.g. on password change); carried in the JWT as "tv".
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
