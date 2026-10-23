-- Keep the original schema migration immutable; tighten canonical code consistency separately.
ALTER TABLE courier_hubs
    DROP CONSTRAINT IF EXISTS ck_courier_hubs_code_normalized;
ALTER TABLE courier_hubs
    ADD CONSTRAINT ck_courier_hubs_code_normalized
    CHECK (normalized_code = upper(trim(code)));
