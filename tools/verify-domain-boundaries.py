#!/usr/bin/env python3
"""Guard domain Maven modules against direct runtime infrastructure dependencies.

This is a deliberately conservative first boundary gate: it catches common
framework, transport, persistence, broker, and cloud SDK dependencies declared
directly by *-domain modules. Test-scoped tooling is allowed. Domain modules may
depend on stable domain contracts from other capabilities.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
FORBIDDEN_GROUP_PREFIXES = (
    "org.springframework",
    "io.quarkus",
    "io.micronaut",
    "org.hibernate",
    "org.postgresql",
    "com.mysql",
    "com.microsoft.sqlserver",
    "org.apache.kafka",
    "org.apache.activemq",
    "com.rabbitmq",
    "software.amazon.awssdk",
    "com.amazonaws",
    "com.azure",
    "com.google.cloud",
    "org.flywaydb",
    "org.liquibase",
    "io.vertx",
    "org.apache.camel",
)
FORBIDDEN_ARTIFACT_TOKENS = (
    "jdbc", "hibernate", "postgresql", "mysql", "mariadb", "mongodb",
    "redis", "kafka", "rabbitmq", "amqp", "servlet", "resteasy",
    "jaxrs", "webflux", "webmvc", "aws-sdk", "azure-", "gcp-",
    "flyway", "liquibase", "persistence-jpa",
)


def _text(node, path):
    child = node.find(path, NS)
    return (child.text or "").strip() if child is not None else ""


def scan(root):
    """Return (checked_count, errors) for domain module POMs below root."""
    root = Path(root)
    poms = sorted(set(root.glob("**/*-domain/pom.xml")) |
                  set(root.glob("**/domain/pom.xml")))
    errors = []
    checked = 0
    for pom in poms:
        try:
            project = ET.parse(pom).getroot()
        except (ET.ParseError, OSError) as exc:
            errors.append(f"{pom.relative_to(root)}: cannot parse POM: {exc}")
            continue
        checked += 1
        for dep in project.findall("./m:dependencies/m:dependency", NS):
            group = _text(dep, "m:groupId")
            artifact = _text(dep, "m:artifactId")
            scope = _text(dep, "m:scope") or "compile"
            if scope in {"test", "provided"}:
                continue
            forbidden_group = any(
                group == prefix or group.startswith(prefix + ".")
                for prefix in FORBIDDEN_GROUP_PREFIXES
            )
            artifact_lower = artifact.lower()
            forbidden_artifact = any(
                token in artifact_lower for token in FORBIDDEN_ARTIFACT_TOKENS
            )
            if forbidden_group or forbidden_artifact:
                errors.append(
                    f"{pom.relative_to(root)}: {scope}-scoped dependency "
                    f"{group}:{artifact} crosses the domain boundary; "
                    "move infrastructure integration to an adapter module"
                )
    if checked == 0:
        errors.append("No domain module POMs were found.")
    return checked, errors


def main():
    root = Path(__file__).resolve().parents[1]
    checked, errors = scan(root)
    if errors:
        print("Domain dependency boundary check failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print(f"Domain dependency boundary check passed: {checked} domain POMs checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
