-- Stage 3 follow-up: flexible rules (flat optional conditions, implicit AND).
-- Old model: a `type` discriminator + a type-specific `params` object.
-- New model: no `type`; `params` is a flat JSON object of optional condition
-- keys (merchant_contains, merchant_regex, min, max, day_from, day_to).
-- A rule matches iff EVERY present condition matches.

UPDATE rules
SET params = CASE
    WHEN type = 'merchant_contains' THEN jsonb_build_object('merchant_contains', params ->> 'value')
    WHEN type = 'merchant_regex'    THEN jsonb_build_object('merchant_regex', params ->> 'pattern')
    WHEN type = 'day_of_month'      THEN jsonb_build_object('day_from', (params ->> 'day')::int,
                                                           'day_to',   (params ->> 'day')::int)
    ELSE params -- amount_range already uses {min, max}
END;

ALTER TABLE rules DROP COLUMN type;
