SET search_path TO accounting, public;

ALTER TABLE syirkah_contract_version
    DROP CONSTRAINT IF EXISTS syirkah_contract_version_syirkah_type_check;

ALTER TABLE syirkah_contract_version
    ADD CONSTRAINT ck_syirkah_contract_version_type
    CHECK (syirkah_type IN ('MUDHARABAH','MUSYARAKAH','INAN','ABDAN','WUJUH','MUFAWADHAH'));
