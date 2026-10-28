-- the platform is only for adults: drop "Menos de 18 anos" from AGE_RANGE, keeping the order of the other options.
-- Old answers with AGE_UNDER_18 in result_questions_triages are kept on purpose.
UPDATE questions_triages q
SET options = (
    SELECT jsonb_agg(e.elem ORDER BY e.ord)
    FROM jsonb_array_elements(q.options) WITH ORDINALITY AS e(elem, ord)
    WHERE e.elem ->> 'code' <> 'AGE_UNDER_18')
WHERE q.code = 'AGE_RANGE'
  AND q.options @> '[{"code": "AGE_UNDER_18"}]';
