-- The app now stores crm_crp_number uppercased with only ASCII letters and digits ("06/12345" -> "0612345"),
-- so existing rows must match. Same rule as CrmCrpNumbers.normalize.
-- Fail with a clear message instead of a bare unique violation when two rows collapse into one credential.
DO $$
DECLARE
    clash RECORD;
BEGIN
    SELECT upper(regexp_replace(crm_crp_number, '[^A-Za-z0-9]', '', 'g')) AS normalized,
           string_agg(crm_crp_number, ', ' ORDER BY crm_crp_number) AS originals
      INTO clash
      FROM psychologists
     GROUP BY 1
    HAVING count(*) > 1
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Cannot normalize crm_crp_number: these values become the same credential "%": %. Fix or remove the duplicates and run the migration again.',
            clash.normalized, clash.originals;
    END IF;

    SELECT crm_crp_number
      INTO clash
      FROM psychologists
     WHERE regexp_replace(crm_crp_number, '[^A-Za-z0-9]', '', 'g') = ''
     LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Cannot normalize crm_crp_number: "%" has no letters or digits. Fix or remove the row and run the migration again.',
            clash.crm_crp_number;
    END IF;
END $$;

UPDATE psychologists
   SET crm_crp_number = upper(regexp_replace(crm_crp_number, '[^A-Za-z0-9]', '', 'g'))
 WHERE crm_crp_number <> upper(regexp_replace(crm_crp_number, '[^A-Za-z0-9]', '', 'g'));
