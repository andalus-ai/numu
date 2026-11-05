#!/usr/bin/env python3
"""Generate a deterministic, source-declared Maven reactor dependency graph.

This intentionally does not claim to calculate Maven's effective model. It reads
POM-declared direct dependencies, resolves the common project/pom/property
expressions available in the scanned POMs, and reports unresolved coordinates.
Use Maven's effective-pom/dependency tooling for profile- and settings-sensitive
runtime resolution.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from typing import Any

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
PROP = re.compile(r"\$\{([^}]+)\}")
DEFAULT_GROUP = "tech.kayys.numou"
SKIP_DIRS = {".git", ".idea", ".mvn", "target", "build", "node_modules"}


def child(node: ET.Element | None, name: str) -> ET.Element | None:
    return node.find(f"m:{name}", NS) if node is not None else None


def value(node: ET.Element | None, name: str, default: str = "") -> str:
    item = child(node, name)
    return (item.text or "").strip() if item is not None else default


def properties(pom: ET.Element) -> dict[str, str]:
    result: dict[str, str] = {}
    props = child(pom, "properties")
    if props is not None:
        for item in list(props):
            key = item.tag.rsplit("}", 1)[-1]
            result[key] = (item.text or "").strip()
    return result


def resolve(raw: str, props: dict[str, str], max_passes: int = 12) -> str:
    current = raw.strip()
    for _ in range(max_passes):
        updated = PROP.sub(lambda m: props.get(m.group(1), m.group(0)), current)
        if updated == current:
            break
        current = updated
    return current


def parse_pom(path: Path, root: Path) -> dict[str, Any]:
    tree = ET.parse(path)
    pom = tree.getroot()
    parent = child(pom, "parent")
    parent_group = value(parent, "groupId")
    parent_version = value(parent, "version")
    props = properties(pom)
    props.update({
        "project.groupId": value(pom, "groupId") or parent_group,
        "pom.groupId": value(pom, "groupId") or parent_group,
        "project.version": value(pom, "version") or parent_version,
        "pom.version": value(pom, "version") or parent_version,
        "project.artifactId": value(pom, "artifactId"),
        "pom.artifactId": value(pom, "artifactId"),
        "project.parent.groupId": parent_group,
        "project.parent.version": parent_version,
        "project.parent.artifactId": value(parent, "artifactId"),
    })
    # Re-resolve properties so aliases such as ${revision} can refer to project properties.
    for key in list(props):
        props[key] = resolve(props[key], props)

    group = resolve(value(pom, "groupId") or parent_group, props)
    artifact = resolve(value(pom, "artifactId"), props)
    version = resolve(value(pom, "version") or parent_version, props)
    rel = path.relative_to(root).as_posix()
    return {
        "path": rel,
        "group_id": group,
        "artifact_id": artifact,
        "version": version,
        "packaging": value(pom, "packaging", "jar"),
        "properties": props,
        "xml": pom,
    }


def coordinate(group: str, artifact: str, version: str) -> str:
    return f"{group}:{artifact}:{version}"


def dependencies(project: dict[str, Any]) -> list[dict[str, Any]]:
    pom = project["xml"]
    props = project["properties"]
    found: list[dict[str, Any]] = []

    def collect(container: ET.Element | None, context: str) -> None:
        if container is None:
            return
        for dep in container.findall("m:dependency", NS):
            raw = {k: value(dep, k) for k in
                   ("groupId", "artifactId", "version", "scope", "type", "classifier", "optional")}
            resolved = {k: resolve(v, props) for k, v in raw.items()}
            scope = resolved["scope"] or "compile"
            unresolved = sorted({m.group(1) for field in ("groupId", "artifactId", "version")
                                 for m in PROP.finditer(resolved[field])})
            found.append({
                "source_path": project["path"],
                "source": coordinate(project["group_id"], project["artifact_id"], project["version"]),
                "group_id": resolved["groupId"],
                "artifact_id": resolved["artifactId"],
                "version": resolved["version"],
                "scope": scope,
                "type": resolved["type"] or "jar",
                "classifier": resolved["classifier"],
                "optional": resolved["optional"].lower() == "true",
                "declaration_context": context,
                "unresolved_properties": unresolved,
            })

    collect(child(pom, "dependencies"), "project")
    profiles = child(pom, "profiles")
    if profiles is not None:
        for profile in profiles.findall("m:profile", NS):
            profile_id = value(profile, "id", "unknown")
            activation = child(profile, "activation")
            active_default = value(activation, "activeByDefault").lower() == "true"
            collect(child(profile, "dependencies"),
                    f"profile:{profile_id}:activeByDefault={str(active_default).lower()}")
    return found


def strongly_connected_components(nodes: set[str], edges: list[tuple[str, str]]) -> list[list[str]]:
    graph: dict[str, list[str]] = defaultdict(list)
    for source, target in edges:
        graph[source].append(target)
    index = 0
    indices: dict[str, int] = {}
    low: dict[str, int] = {}
    stack: list[str] = []
    on_stack: set[str] = set()
    components: list[list[str]] = []

    def visit(node: str) -> None:
        nonlocal index
        indices[node] = low[node] = index
        index += 1
        stack.append(node)
        on_stack.add(node)
        for target in sorted(graph[node]):
            if target not in indices:
                visit(target)
                low[node] = min(low[node], low[target])
            elif target in on_stack:
                low[node] = min(low[node], indices[target])
        if low[node] == indices[node]:
            component: list[str] = []
            while True:
                item = stack.pop()
                on_stack.remove(item)
                component.append(item)
                if item == node:
                    break
            if len(component) > 1 or any(a == b == node for a, b in edges):
                components.append(sorted(component))

    for node in sorted(nodes):
        if node not in indices:
            visit(node)
    return sorted(components)


def write_outputs(out: Path, projects: list[dict[str, Any]], deps: list[dict[str, Any]],
                  group_prefix: str, cycle_scopes: set[str]) -> dict[str, Any]:
    by_coord: dict[str, list[str]] = defaultdict(list)
    by_ga: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    for project in projects:
        if project["group_id"] and project["artifact_id"]:
            by_ga[(project["group_id"], project["artifact_id"])].append(project)
            if project["version"]:
                by_coord[coordinate(project["group_id"], project["artifact_id"], project["version"])].append(project["path"])

    for dep in deps:
        key = coordinate(dep["group_id"], dep["artifact_id"], dep["version"])
        dep["target_paths"] = sorted(by_coord.get(key, [])) if dep["version"] else []
        dep["target_coordinates"] = [key] if dep["target_paths"] else []
        dep["is_internal"] = (dep["group_id"] == group_prefix or dep["group_id"].startswith(group_prefix + "."))
        if not dep["version"] and not dep["unresolved_properties"]:
            candidates = by_ga.get((dep["group_id"], dep["artifact_id"]), [])
            if len(candidates) == 1:
                dep["target_paths"] = [candidates[0]["path"]]
                dep["target_coordinates"] = [candidates[0]["coordinate"]]
        dep["resolution"] = ("unresolved-property" if dep["unresolved_properties"] else
                             "resolved-internal" if dep["target_paths"] and dep["version"] else
                             "resolved-internal-version-unspecified" if dep["target_paths"] else
                             "unmatched-internal" if dep["is_internal"] else "external")

    internal = [d for d in deps if d["resolution"] in
                {"resolved-internal", "resolved-internal-version-unspecified"}]
    cycle_edges = [(d["source"], d["target_coordinates"][0])
                   for d in internal if d["scope"] in cycle_scopes and len(d["target_coordinates"]) == 1]
    nodes = {p["coordinate"] for p in projects if p["coordinate"]}
    cycles = strongly_connected_components(nodes, cycle_edges)

    out.mkdir(parents=True, exist_ok=True)
    project_rows = [{k: p[k] for k in ("path", "coordinate", "packaging")} for p in projects]
    payload = {
        "schema_version": 1,
        "analysis_kind": "pom-declared-direct-dependencies",
        "limitations": [
            "Not Maven effective-model output; inherited dependencies are not synthesized.",
            "Profile dependencies are reported separately and are not assumed active.",
            "No transitive dependency expansion, settings.xml, mirrors, BOM import, or plugin resolution.",
            "Unresolved property expressions and internal coordinates without scanned targets are retained as diagnostics.",
            "A dependency without an explicit version is linked by groupId/artifactId only when exactly one scanned project matches; the managed version itself is not inferred.",
        ],
        "cycle_scopes": sorted(cycle_scopes),
        "project_count": len(projects),
        "dependency_declaration_count": len(deps),
        "internal_resolved_edge_count": len(internal),
        "unresolved_property_count": sum(bool(d["unresolved_properties"]) for d in deps),
        "unmatched_internal_count": sum(d["resolution"] == "unmatched-internal" for d in deps),
        "duplicate_coordinates": {k: sorted(v) for k, v in by_coord.items() if len(v) > 1},
        "projects": project_rows,
        "dependencies": sorted(deps, key=lambda d: (d["source_path"], d["declaration_context"],
                                                       d["group_id"], d["artifact_id"], d["scope"])),
        "strongly_connected_components": cycles,
    }
    (out / "maven-dependency-graph.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    fields = ["source_path", "source", "group_id", "artifact_id", "version", "scope",
              "type", "classifier", "optional", "declaration_context", "resolution",
              "target_paths", "target_coordinates", "unresolved_properties"]
    with (out / "maven-dependency-edges.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        for dep in payload["dependencies"]:
            row = dict(dep)
            row["target_paths"] = ";".join(row["target_paths"])
            row["target_coordinates"] = ";".join(row["target_coordinates"])
            row["unresolved_properties"] = ";".join(row["unresolved_properties"])
            writer.writerow({key: row[key] for key in fields})

    lines = ["digraph maven_modules {", "  rankdir=LR;"]
    for node in sorted(nodes):
        lines.append(f'  "{node}";')
    for dep in internal:
        if dep["scope"] in cycle_scopes:
            target = dep["target_coordinates"][0]
            lines.append(f'  "{dep["source"]}" -> "{target}" [label="{dep["scope"]}"];')
    lines.append("}")
    (out / "maven-dependency-graph.dot").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repository root (default: current directory)")
    parser.add_argument("--output-dir", type=Path, default=Path("target/architecture-dependency-graph"))
    parser.add_argument("--group-prefix", default=DEFAULT_GROUP, help="internal Maven groupId prefix")
    parser.add_argument("--cycle-scopes", default="compile,runtime",
                        help="comma-separated scopes used for SCC/cycle analysis")
    parser.add_argument("--fail-on-cycle", action="store_true",
                        help="return exit code 2 when a cycle is found in selected scopes")
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        parser.error(f"repository root does not exist: {root}")

    pom_paths = sorted(p for p in root.rglob("pom.xml")
                       if not any(part in SKIP_DIRS for part in p.relative_to(root).parts))
    projects: list[dict[str, Any]] = []
    errors: list[str] = []
    for path in pom_paths:
        try:
            project = parse_pom(path, root)
            project["coordinate"] = (coordinate(project["group_id"], project["artifact_id"], project["version"])
                                      if project["group_id"] and project["artifact_id"] and project["version"] else "")
            projects.append(project)
        except (ET.ParseError, OSError) as exc:
            errors.append(f"{path.relative_to(root)}: {exc}")

    deps = [dep for project in projects for dep in dependencies(project)]
    payload = write_outputs(args.output_dir.resolve(), projects, deps, args.group_prefix,
                            {s.strip() for s in args.cycle_scopes.split(",") if s.strip()})
    payload["pom_parse_errors"] = errors
    (args.output_dir.resolve() / "maven-dependency-graph.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(f"POM files: {len(pom_paths)}; parsed projects: {len(projects)}; parse errors: {len(errors)}")
    print(f"Direct dependency declarations: {len(deps)}; resolved internal edges: {payload['internal_resolved_edge_count']}")
    print(f"Unresolved properties: {payload['unresolved_property_count']}; unmatched internal coordinates: {payload['unmatched_internal_count']}")
    print(f"Strongly connected components (scopes={','.join(sorted({s.strip() for s in args.cycle_scopes.split(',') if s.strip()}))}): {len(payload['strongly_connected_components'])}")
    print(f"Outputs: {args.output_dir.resolve()}")
    if errors:
        print("\n".join(errors), file=sys.stderr)
    return 2 if args.fail_on_cycle and payload["strongly_connected_components"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
