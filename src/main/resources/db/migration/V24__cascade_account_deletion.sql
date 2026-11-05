-- Right to erasure (LGPD art. 18, VI): DELETE /me removes the users row and everything that hangs from it.
-- refresh_tokens (V23) and result_questions_triages (V4) already cascade; these five did not.
-- audit_log has no FK to users on purpose (V17): it keeps ids only, and the record that an access happened stays.

ALTER TABLE psychologists
    DROP CONSTRAINT psychologists_user_id_fkey,
    ADD CONSTRAINT psychologists_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE dependents
    DROP CONSTRAINT dependents_user_id_fkey,
    ADD CONSTRAINT dependents_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE triages
    DROP CONSTRAINT triages_dependent_id_fkey,
    ADD CONSTRAINT triages_dependent_id_fkey FOREIGN KEY (dependent_id) REFERENCES dependents (user_id) ON DELETE CASCADE;

ALTER TABLE consents
    DROP CONSTRAINT consents_user_id_fkey,
    ADD CONSTRAINT consents_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE dependent_nickname_history
    DROP CONSTRAINT dependent_nickname_history_dependent_id_fkey,
    ADD CONSTRAINT dependent_nickname_history_dependent_id_fkey
        FOREIGN KEY (dependent_id) REFERENCES dependents (user_id) ON DELETE CASCADE;
