CREATE TABLE dependents (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);