CREATE TABLE triages (
    id BIGSERIAL PRIMARY KEY,
    dependent_id BIGINT NOT NULL UNIQUE REFERENCES dependents (user_id),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE questions_triages (
    id BIGSERIAL PRIMARY KEY,
    question_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE result_questions_triages (
    id BIGSERIAL PRIMARY KEY,
    triage_id BIGINT NOT NULL REFERENCES triages (id) ON DELETE CASCADE,
    question_id BIGINT NOT NULL REFERENCES questions_triages (id),
    value_result TEXT, -- TODO: decide checkbox vs input
    UNIQUE (triage_id, question_id)
);