#!/usr/bin/env python3
"""Build an effective Maven dependency graph by asking Maven to evaluate each POM.

Unlike the source-POM scanner, this tool delegates inheritance, properties,
dependencyManagement, BOM imports and profile activation to the installed Maven
runtime. Results are environment-specific; record the Maven/JDK/settings/profile
context alongside every report.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from typing import Any

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SKIP_DIRS = {".git", ".idea", ".mvn", "target", "build", "node_modules"}


def get_text(node: ET.Element | None, name: str, default: str = "") -> str:
    if node is None:
        return default
    item = node.find(f"m:{name}", NS)
    return (item.text or "").strip() if item is not None else default


def read_effective_pom(path: Path) -> dict[str, Any]:
    root = ET.parse(path).getroot()
    group = get_text(root, "groupId") or get_text(root.find("m:parent", NS), "groupId")
    version = get_text(root, "version") or get_text(root.find("m:parent", NS), "version")
    artifact = get_text(root, "artifactId")
    deps: list[dict[str, str]] = []
    container = root.find("m:dependencies", NS)
    if container is not None:
        for dep in container.findall("m:dependency", NS):
            deps.append({
                "group_id": get_text(dep, "groupId"),
                "artifact_id": get_text(dep, "artifactId"),
                "version": get_text(dep, "version"),
                "scope": get_text(dep, "scope", "compile") or "compile",
                "type": get_text(dep, "type", "jar") or "jar",
                "classifier": get_text(dep, "classifier"),
                "optional": get_text(dep, "optional").lower() == "true",
            })
    return {
        "group_id": group,
        "artifact_id": artifact,
        "version": version,
        "packaging": get_text(root, "packaging", "jar") or "jar",
        "dependencies": deps,
    }


def coord(group: str, artifact: str, version: str) -> str:
    return f"{group}:{artifact}:{version}"


def scc(nodes: set[str], edges: list[tuple[str, str]]) -> list[list[str]]:
    graph: dict[str, list[str]] = defaultdict(list)
    for source, target in edges:
        graph[source].append(target)
    index = 0
    indices: dict[str, int] = {}
    low: dict[str, int] = {}
    stack: list[str] = []
    active: set[str] = set()
    found: list[list[str]] = []

    def visit(node: str) -> None:
        nonlocal index
        indices[node] = low[node] = index
        index += 1
        stack.append(node)
        active.add(node)
        for target in sorted(graph[node]):
            if target not in indices:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in active:
                low[node] = min(low[node], indices[target])
        if low[node] == indices[node]:
            component: list[str] = []
            while True:
                current = stack.pop()
                active.remove(current)
                component.append(current)
                if current == node:
                    break
            if len(component) > 1 or any(a == b == node for a, b in edges):
                found.append(sorted(component))

    for node in sorted(nodes):
        if node not in indices:
            visit(node)
    return sorted(found)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--output-dir", type=Path, default=Path("target/architecture-effective-graph"))
    parser.add_argument("--maven", default="mvn", help="Maven executable")
    parser.add_argument("--settings", type=Path, help="Maven settings.xml")
    parser.add_argument("--profiles", default="", help="Maven profile IDs, comma-separated")
    parser.add_argument("--offline", action="store_true", help="run Maven with -o")
    parser.add_argument("--timeout-seconds", type=int, default=180,
                        help="timeout for each effective-pom invocation")
    parser.add_argument("--group-prefix", default="tech.kayys.numou")
    parser.add_argument("--cycle-scopes", default="compile,runtime")
    parser.add_argument("--strict", action="store_true",
                        help="return non-zero if any POM could not be evaluated")
    args = parser.parse_args()

    root = args.root.resolve()
    if not root.is_dir():
        parser.error(f"repository root does not exist: {root}")
    if args.timeout_seconds < 1:
        parser.error("--timeout-seconds must be positive")
    try:
        version_result = subprocess.run([args.maven, "-version"], check=False, capture_output=True,
                                        text=True, timeout=args.timeout_seconds)
    except (OSError, subprocess.TimeoutExpired) as exc:
        print(f"Cannot execute Maven: {exc}", file=sys.stderr)
        return 2
    maven_version = (version_result.stdout or version_result.stderr).splitlines()[:3]
    if version_result.returncode != 0:
        print("Maven version command failed", file=sys.stderr)
        return 2

    pom_paths = sorted(p for p in root.rglob("pom.xml")
                       if not any(part in SKIP_DIRS for part in p.relative_to(root).parts))
    projects: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []
    base_args = [args.maven]
    if args.offline:
        base_args.append("-o")
    if args.settings:
        base_args.extend(["-s", str(args.settings.resolve())])
    if args.profiles:
        base_args.extend(["-P", args.profiles])

    with tempfile.TemporaryDirectory(prefix="numou-effective-pom-") as tmp:
        temp_root = Path(tmp)
        for index, pom in enumerate(pom_paths):
            effective_file = temp_root / f"effective-{index}.xml"
            command = base_args + ["-f", str(pom), "help:effective-pom",
                                   f"-Doutput={effective_file}", "-Dverbose"]
            try:
                result = subprocess.run(command, cwd=root, check=False, capture_output=True,
                                        text=True, timeout=args.timeout_seconds)
                if result.returncode != 0 or not effective_file.is_file():
                    failures.append({
                        "pom": pom.relative_to(root).as_posix(),
                        "return_code": str(result.returncode),
                        "stderr": (result.stderr or result.stdout)[-4000:],
                    })
                    continue
                effective = read_effective_pom(effective_file)
                project = {
                    "path": pom.relative_to(root).as_posix(),
                    "coordinate": coord(effective["group_id"], effective["artifact_id"], effective["version"]),
                    "group_id": effective["group_id"],
                    "artifact_id": effective["artifact_id"],
                    "version": effective["version"],
                    "packaging": effective["packaging"],
                    "dependencies": effective["dependencies"],
                }
                projects.append(project)
            except (OSError, ET.ParseError, subprocess.TimeoutExpired) as exc:
                failures.append({"pom": pom.relative_to(root).as_posix(), "error": str(exc)})

    by_coord: dict[str, list[str]] = defaultdict(list)
    for project in projects:
        by_coord[project["coordinate"]].append(project["path"])
    project_coords = set(by_coord)
    cycle_scopes = {item.strip() for item in args.cycle_scopes.split(",") if item.strip()}
    edges: list[dict[str, Any]] = []
    for project in projects:
        for dep in project["dependencies"]:
            target = coord(dep["group_id"], dep["artifact_id"], dep["version"])
            internal_group = dep["group_id"] == args.group_prefix or dep["group_id"].startswith(args.group_prefix + ".")
            matches = sorted(by_coord.get(target, []))
            edges.append({
                "source_path": project["path"],
                "source": project["coordinate"],
                **dep,
                "target": target,
                "target_paths": matches,
                "is_internal_group": internal_group,
                "resolution": "resolved-internal" if matches else "unmatched-internal" if internal_group else "external",
            })

    cycle_edges = [(edge["source"], edge["target"]) for edge in edges
                   if edge["target_paths"] and edge["scope"] in cycle_scopes]
    cycles = scc(project_coords, cycle_edges)
    payload = {
        "schema_version": 1,
        "analysis_kind": "maven-effective-pom-dependencies",
        "environment": {
            "maven_executable": args.maven,
            "maven_version": maven_version,
            "profiles": [p for p in args.profiles.split(",") if p],
            "settings_path": str(args.settings.resolve()) if args.settings else None,
            "offline": args.offline,
            "cycle_scopes": sorted(cycle_scopes),
            "cwd": str(root),
        },
        "project_count": len(projects),
        "pom_count": len(pom_paths),
        "evaluation_failure_count": len(failures),
        "dependency_edge_count": len(edges),
        "resolved_internal_edge_count": sum(edge["resolution"] == "resolved-internal" for edge in edges),
        "unmatched_internal_count": sum(edge["resolution"] == "unmatched-internal" for edge in edges),
        "duplicate_coordinates": {key: sorted(value) for key, value in by_coord.items() if len(value) > 1},
        "strongly_connected_components": cycles,
        "projects": [{k: project[k] for k in ("path", "coordinate", "packaging")} for project in projects],
        "dependencies": sorted(edges, key=lambda edge: (edge["source_path"], edge["group_id"],
                                                          edge["artifact_id"], edge["scope"])),
        "evaluation_failures": failures,
    }
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    (output / "maven-effective-dependency-graph.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    fields = ["source_path", "source", "group_id", "artifact_id", "version", "scope",
              "type", "classifier", "optional", "target", "target_paths", "resolution"]
    with (output / "maven-effective-dependency-edges.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for edge in payload["dependencies"]:
            row = dict(edge)
            row["target_paths"] = ";".join(row["target_paths"])
            writer.writerow({key: row[key] for key in fields})

    print(f"Maven: {' | '.join(maven_version)}")
    print(f"POMs: {len(pom_paths)}; evaluated: {len(projects)}; failures: {len(failures)}")
    print(f"Effective dependency edges: {len(edges)}; internal resolved: {payload['resolved_internal_edge_count']}")
    print(f"Unmatched internal: {payload['unmatched_internal_count']}; SCCs: {len(cycles)}")
    print(f"Output: {output}")
    return 2 if args.strict and failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
