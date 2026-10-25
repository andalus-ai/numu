#!/usr/bin/env python3
"""Enforce declared cross-capability Maven dependency-direction rules.

Rules are intentionally explicit and incremental: this verifier checks direct
POM dependencies only, and it does not claim that undeclared boundaries are
automatically inferred from package names or repository layout.
"""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
CONFIG_PATH = Path(__file__).with_name("capability-boundaries.json")


def child_text(node, name):
    child = node.find(f"m:{name}", NS)
    return (child.text or "").strip() if child is not None else ""


def load_rules(path=CONFIG_PATH):
    with Path(path).open(encoding="utf-8") as stream:
        config = json.load(stream)
    if config.get("version") != 1:
        raise ValueError("unsupported capability boundary rules version")
    rules = config.get("rules")
    if not isinstance(rules, list) or not rules:
        raise ValueError("capability boundary configuration must contain rules")
    seen = set()
    for rule in rules:
        required = (
            "id", "consumer_artifact_prefixes",
            "forbidden_dependency_artifact_prefixes",
            "forbidden_dependency_group", "rationale",
        )
        missing = [key for key in required if not rule.get(key)]
        if missing:
            raise ValueError(f"rule is missing required fields: {', '.join(missing)}")
        if rule["id"] in seen:
            raise ValueError(f"duplicate capability boundary rule id: {rule['id']}")
        seen.add(rule["id"])
    return rules


def scan(root, config_path=CONFIG_PATH):
    """Return (checked_pom_count, errors) for configured dependency rules."""
    root = Path(root)
    errors = []
    try:
        rules = load_rules(config_path)
    except (OSError, json.JSONDecodeError, ValueError) as exc:
        return 0, [f"cannot load capability boundary rules: {exc}"]

    checked = 0
    for pom in sorted(root.glob("**/pom.xml")):
        try:
            project = ET.parse(pom).getroot()
        except (ET.ParseError, OSError) as exc:
            errors.append(f"{pom.relative_to(root)}: cannot parse POM: {exc}")
            continue
        checked += 1
        consumer_artifact = child_text(project, "artifactId")
        if not consumer_artifact:
            continue
        for rule in rules:
            if not any(consumer_artifact.startswith(prefix)
                       for prefix in rule["consumer_artifact_prefixes"]):
                continue
            for dep in project.findall("./m:dependencies/m:dependency", NS):
                group = child_text(dep, "groupId")
                artifact = child_text(dep, "artifactId")
                scope = child_text(dep, "scope") or "compile"
                if group != rule["forbidden_dependency_group"]:
                    continue
                if not any(artifact.startswith(prefix)
                           for prefix in rule["forbidden_dependency_artifact_prefixes"]):
                    continue
                # Test fixtures may reference extension types without making
                # them part of the production capability dependency graph.
                # 'provided' still affects compilation and is therefore forbidden.
                if scope == "test":
                    continue
                errors.append(
                    f"{pom.relative_to(root)}: capability rule '{rule['id']}' "
                    f"forbids {scope}-scoped dependency {group}:{artifact}; "
                    f"{rule['rationale']}"
                )

    if checked == 0:
        errors.append("No Maven POM files were found.")
    return checked, errors


def main():
    root = Path(__file__).resolve().parents[1]
    checked, errors = scan(root)
    if errors:
        print("Capability dependency-direction check failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print(f"Capability dependency-direction check passed: {checked} POMs checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
