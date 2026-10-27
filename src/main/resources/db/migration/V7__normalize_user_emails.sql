-- The app now lowercases emails on register and login, so existing rows must match.
UPDATE users SET email = lower(email) WHERE email <> lower(email);

-- Emails are unique regardless of case; also catches concurrent registrations that differ only by case.
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));
