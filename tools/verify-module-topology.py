#!/usr/bin/env python3
"""Validate top-level Maven capability reactors resolve the tracked framework parent."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
EXPECTED = ROOT / "core" / "framework" / "pom.xml"
errors = []
checked = 0

if not EXPECTED.is_file():
    errors.append("Expected framework parent is missing: core/framework/pom.xml")

for pom in sorted(ROOT.glob("*/pom.xml")):
    try:
        root = ET.parse(pom).getroot()
    except ET.ParseError as exc:
        errors.append(f"{pom.relative_to(ROOT)}: invalid XML: {exc}")
        continue

    parent = root.find("m:parent", NS)
    if parent is None:
        continue
    artifact = parent.findtext("m:artifactId", default="", namespaces=NS)
    group = parent.findtext("m:groupId", default="", namespaces=NS)
    if group != "tech.kayys.numou" or artifact != "numou-framework":
        continue

    checked += 1
    relative = parent.findtext("m:relativePath", default="../pom.xml", namespaces=NS).strip()
    resolved = (pom.parent / relative).resolve()
    if not resolved.is_file():
        errors.append(f"{pom.relative_to(ROOT)}: parent path does not exist: {relative}")
        continue

    try:
        parent_root = ET.parse(resolved).getroot()
    except ET.ParseError as exc:
        errors.append(f"{pom.relative_to(ROOT)}: parent XML is invalid: {exc}")
        continue
    parent_artifact = parent_root.findtext("m:artifactId", default="", namespaces=NS)
    parent_group = parent_root.findtext("m:groupId", default="", namespaces=NS)
    parent_version = parent_root.findtext("m:version", default="", namespaces=NS)
    version = parent.findtext("m:version", default="", namespaces=NS)
    if (parent_group, parent_artifact, parent_version) != (group, artifact, version):
        errors.append(
            f"{pom.relative_to(ROOT)}: resolved parent GAV "
            f"{parent_group}:{parent_artifact}:{parent_version} does not match "
            f"declared {group}:{artifact}:{version}"
        )
    if resolved != EXPECTED.resolve():
        errors.append(
            f"{pom.relative_to(ROOT)}: expected shared parent at "
            f"core/framework/pom.xml, got {resolved.relative_to(ROOT)}"
        )

if checked == 0:
    errors.append("No top-level capability reactors were checked.")

if errors:
    print("Maven module topology check failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    sys.exit(1)

print(f"Maven module topology check passed: {checked} top-level capability reactors use core/framework/pom.xml.")
