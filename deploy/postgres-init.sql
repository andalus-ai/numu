-- Dev bootstrap. The application role is deliberately NOT a superuser and has no BYPASSRLS, so row-level
-- security (tenant isolation) is actually enforced locally exactly as in production.
CREATE ROLE numou_app LOGIN PASSWORD 'numou_app' NOSUPERUSER NOBYPASSRLS;
CREATE DATABASE numou OWNER numou_app;
