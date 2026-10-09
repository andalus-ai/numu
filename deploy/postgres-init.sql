-- Dev bootstrap. The application role is deliberately NOT a superuser and has no BYPASSRLS, so row-level
-- security (tenant isolation) is actually enforced locally exactly as in production.
CREATE ROLE syirkah_app LOGIN PASSWORD 'syirkah_app' NOSUPERUSER NOBYPASSRLS;
CREATE DATABASE syirkah OWNER syirkah_app;
