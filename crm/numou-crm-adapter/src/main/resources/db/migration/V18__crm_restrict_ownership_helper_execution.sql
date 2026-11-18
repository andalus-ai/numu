-- V18: keep the relationship-validation helper internal to the trusted executor.
-- PostgreSQL grants EXECUTE on newly created functions to PUBLIC by default.
-- The executor itself was already restricted in V17; close the helper's gap too.
revoke all on function crm_assert_related_tenant(text, uuid, uuid) from public;
