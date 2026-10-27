ALTER TABLE result_questions_triages
    DROP CONSTRAINT result_questions_triages_triage_id_question_id_key;

ALTER TABLE result_questions_triages
    ALTER COLUMN value_result SET NOT NULL;

ALTER TABLE result_questions_triages
    ADD CONSTRAINT uq_result_triage_question_value
        UNIQUE (triage_id, question_id, value_result);