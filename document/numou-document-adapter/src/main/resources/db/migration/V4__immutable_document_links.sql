-- Evidence relationships are historical facts. Retargeting or deleting a link would
-- rewrite the evidence trail, so enforce the append-only contract in PostgreSQL too.
CREATE OR REPLACE FUNCTION reject_document_link_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'document_links are append-only; create a new evidence link instead'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER trg_document_links_append_only
BEFORE UPDATE OR DELETE ON document_links
FOR EACH ROW
EXECUTE FUNCTION reject_document_link_mutation();
