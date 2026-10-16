#!/usr/bin/env python3
"""Validate Maven reactor metadata and enforce basic domain-layer purity.

No third-party Python packages are required. The script deliberately validates
all repository POMs, not only modules currently listed in the root reactor.
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
FORBIDDEN_DOMAIN_IMPORTS = (
    "jakarta.ws.rs.",
    "jakarta.servlet.",
    "io.quarkus.",
    "org.springframework.",
    "javax.sql.",
    "java.sql.",
)


def child_text(node: ET.Element, path: str) -> str | None:
    found = node.find(path, NS)
    if found is None or found.text is None:
        return None
    return found.text.strip()


def coordinates(path: Path, root: ET.Element) -> tuple[str, str, str]:
    group = child_text(root, "m:groupId")
    version = child_text(root, "m:version")
    if not group:
        parent = root.find("m:parent", NS)
        group = child_text(parent, "m:groupId") if parent is not None else None
    if not version:
        parent = root.find("m:parent", NS)
        version = child_text(parent, "m:version") if parent is not None else None
    artifact = child_text(root, "m:artifactId")
    if not group or not artifact:
        raise ValueError(f"{path}: Maven groupId/artifactId could not be resolved")
    return group, artifact, version or "UNSPECIFIED"


def main() -> int:
    errors: list[str] = []
    pom_paths = sorted(p for p in ROOT.rglob("pom.xml") if "target" not in p.parts and ".git" not in p.parts)
    parsed: dict[Path, ET.Element] = {}

    for path in pom_paths:
        try:
            parsed[path] = ET.parse(path).getroot()
        except ET.ParseError as exc:
            errors.append(f"{path.relative_to(ROOT)}: invalid XML: {exc}")

    coordinates_by_path: dict[Path, tuple[str, str, str]] = {}
    artifact_paths: dict[tuple[str, str], list[Path]] = defaultdict(list)
    for path, pom in parsed.items():
        try:
            coords = coordinates(path, pom)
            coordinates_by_path[path] = coords
            artifact_paths[(coords[0], coords[1])].append(path)
        except ValueError as exc:
            errors.append(str(exc))

    for (group, artifact), paths in artifact_paths.items():
        if len(paths) > 1:
            rendered = ", ".join(str(p.relative_to(ROOT)) for p in paths)
            errors.append(f"duplicate Maven coordinate {group}:{artifact}: {rendered}")

    for path, pom in parsed.items():
        for module in pom.findall("m:modules/m:module", NS):
            if not module.text:
                errors.append(f"{path.relative_to(ROOT)}: empty module declaration")
                continue
            module_path = (path.parent / module.text.strip()).resolve()
            if module_path.is_dir():
                module_path = module_path / "pom.xml"
            if not module_path.is_file():
                errors.append(f"{path.relative_to(ROOT)}: declared module does not exist: {module.text.strip()}")
            elif module_path not in parsed:
                errors.append(f"{path.relative_to(ROOT)}: declared module POM was not parsed: {module.text.strip()}")

    known_artifacts = {(group, artifact) for group, artifact, _ in coordinates_by_path.values()}
    graph: dict[Path, set[Path]] = {path: set() for path in parsed}
    artifact_to_paths = {(g, a): p for p, (g, a, _) in coordinates_by_path.items()}
    for path, pom in parsed.items():
        deps = pom.findall("m:dependencies/m:dependency", NS)
        for dep in deps:
            group = child_text(dep, "m:groupId")
            artifact = child_text(dep, "m:artifactId")
            if not group or not artifact:
                continue
            if group == "tech.kayys.numou" and (group, artifact) not in known_artifacts:
                errors.append(f"{path.relative_to(ROOT)}: unresolved internal dependency {group}:{artifact}")
            target = artifact_to_paths.get((group, artifact))
            if target is not None:
                graph[path].add(target)

    visiting: set[Path] = set()
    visited: set[Path] = set()
    cycle_reported: set[tuple[str, ...]] = set()

    def visit(node: Path, stack: list[Path]) -> None:
        if node in visiting:
            start = stack.index(node) if node in stack else 0
            cycle = tuple(sorted(str(p.relative_to(ROOT)) for p in stack[start:] + [node]))
            if cycle not in cycle_reported:
                cycle_reported.add(cycle)
                errors.append("Maven dependency cycle: " + " -> ".join(cycle))
            return
        if node in visited:
            return
        visiting.add(node)
        stack.append(node)
        for nxt in sorted(graph[node]):
            visit(nxt, stack)
        stack.pop()
        visiting.remove(node)
        visited.add(node)

    for path in sorted(graph):
        visit(path, [])

    for path in ROOT.rglob("src/main/java/**/*.java"):
        if "target" in path.parts or ".git" in path.parts:
            continue
        module_pom = path
        while module_pom != ROOT and module_pom.name != "pom.xml":
            module_pom = module_pom.parent
        if module_pom == ROOT or module_pom not in coordinates_by_path:
            continue
        artifact = coordinates_by_path[module_pom][1]
        if not artifact.endswith("-domain"):
            continue
        try:
            source = path.read_text(encoding="utf-8")
        except OSError as exc:
            errors.append(f"{path.relative_to(ROOT)}: unable to read source: {exc}")
            continue
        for forbidden in FORBIDDEN_DOMAIN_IMPORTS:
            if f"import {forbidden}" in source:
                errors.append(
                    f"{path.relative_to(ROOT)}: domain module imports forbidden infrastructure API {forbidden}"
                )

    if errors:
        print("Reactor consistency failed:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1
    print(f"Reactor consistency passed: {len(parsed)} POMs checked; module paths, internal dependencies, "
          "dependency cycles, and domain purity verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
