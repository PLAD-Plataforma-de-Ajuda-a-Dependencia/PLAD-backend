CREATE TABLE psychologists (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    graduate VARCHAR(150),
    credential_type crm_crp_type NOT NULL,
    crm_crp_number VARCHAR(30) NOT NULL UNIQUE,
    is_validate BOOLEAN NOT NULL DEFAULT false,
    validate_time TIMESTAMP DEFAULT now()
);