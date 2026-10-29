-- A btree entry over the raw value_result fails for long free text (limit ~2700 bytes, e.g. ~700 emojis).
-- Uniqueness is enforced on the md5 of the value instead, which has a fixed size.
ALTER TABLE result_questions_triages
    DROP CONSTRAINT uq_result_triage_question_value;

CREATE UNIQUE INDEX uq_result_triage_question_value
    ON result_questions_triages (triage_id, question_id, md5(value_result));
