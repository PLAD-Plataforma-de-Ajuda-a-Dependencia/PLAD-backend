CREATE TYPE user_type AS ENUM('dependente', 'psicologo');
CREATE TYPE status_reuniao AS ENUM ('andamento', 'agendada', 'encerrada');
CREATE TYPE crm_crp_type AS ENUM ('CRM', 'CRP');
CREATE TYPE sex AS ENUM ('masculino', 'feminino', 'nao_binario', 'transexual', 'outro');

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    real_name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    birth_date DATE NOT NULL,
    gender sex NOT NULL,
    type_user user_type NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);