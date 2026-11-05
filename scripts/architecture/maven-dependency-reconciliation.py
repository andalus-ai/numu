#!/usr/bin/env python3
"""Reconcile source-declared POM dependencies with Maven effective-POM edges."""
from __future__ import annotations
import argparse, csv, json, sys
from collections import defaultdict
from pathlib import Path
from typing import Any

DECLARED_KIND = "pom-declared-direct-dependencies"
EFFECTIVE_KIND = "maven-effective-pom-dependencies"

def load_graph(path: Path, expected_kind: str) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read {path}: {exc}") from exc
    if data.get("analysis_kind") != expected_kind:
        raise ValueError(f"{path}: expected analysis_kind {expected_kind!r}, got {data.get('analysis_kind')!r}")
    if not isinstance(data.get("projects"), list) or not isinstance(data.get("dependencies"), list):
        raise ValueError(f"{path}: projects and dependencies must be arrays")
    return data

def key(edge: dict[str, Any]) -> tuple[str, str, str, str, str]:
    return (edge.get("source_path", ""), edge.get("group_id", ""), edge.get("artifact_id", ""),
            edge.get("type") or "jar", edge.get("classifier") or "")

def normalized_scope(value: Any) -> str:
    return value or "compile"

def reconcile(declared: dict[str, Any], effective: dict[str, Any]) -> dict[str, Any]:
    effective_by_key: dict[tuple[str, str, str, str, str], list[dict[str, Any]]] = defaultdict(list)
    for edge in effective["dependencies"]:
        effective_by_key[key(edge)].append(edge)
    used: set[int] = set()
    matches, declared_only = [], []
    ordered = sorted(declared["dependencies"], key=lambda d: (
        d.get("declaration_context", "") != "project", d.get("source_path", ""),
        d.get("group_id", ""), d.get("artifact_id", ""), d.get("scope", "")))
    for declaration in ordered:
        candidate = next((e for e in effective_by_key.get(key(declaration), []) if id(e) not in used), None)
        context = declaration.get("declaration_context", "project")
        if candidate is None:
            declared_only.append({
                "classification": "profile-not-in-effective-model" if str(context).startswith("profile:")
                    else "declared-not-in-effective-model",
                "declaration": declaration})
            continue
        used.add(id(candidate))
        ds, es = normalized_scope(declaration.get("scope")), normalized_scope(candidate.get("scope"))
        dv, ev = declaration.get("version", ""), candidate.get("version", "")
        matches.append({
            "source_path": declaration.get("source_path", ""), "source": declaration.get("source", ""),
            "group_id": declaration.get("group_id", ""), "artifact_id": declaration.get("artifact_id", ""),
            "type": declaration.get("type") or "jar", "classifier": declaration.get("classifier") or "",
            "declaration_context": context, "declared_version": dv, "effective_version": ev,
            "version_changed": bool(dv and ev and dv != ev),
            "version_managed_or_inherited": not bool(dv) and bool(ev),
            "declared_scope": ds, "effective_scope": es, "scope_changed": ds != es,
            "effective_target": candidate.get("target", ""),
            "effective_target_paths": candidate.get("target_paths", []),
            "effective_resolution": candidate.get("resolution", "")})
    effective_only = [{"classification": "effective-only-origin-unattributed", "edge": e}
                      for e in effective["dependencies"] if id(e) not in used]
    dp = {p.get("path") for p in declared["projects"]}
    ep = {p.get("path") for p in effective["projects"]}
    cycles, failures = effective.get("strongly_connected_components", []), effective.get("evaluation_failures", [])
    summary = {
        "declared_project_count": len(dp), "effective_project_count": len(ep),
        "projects_only_in_declared": len(dp - ep), "projects_only_in_effective": len(ep - dp),
        "matched_declaration_count": len(matches),
        "version_changed_count": sum(m["version_changed"] for m in matches),
        "version_managed_or_inherited_count": sum(m["version_managed_or_inherited"] for m in matches),
        "scope_changed_count": sum(m["scope_changed"] for m in matches),
        "declared_not_effective_count": sum(x["classification"] == "declared-not-in-effective-model" for x in declared_only),
        "profile_not_effective_count": sum(x["classification"] == "profile-not-in-effective-model" for x in declared_only),
        "effective_only_count": len(effective_only),
        "effective_unmatched_internal_count": effective.get("unmatched_internal_count", 0),
        "effective_evaluation_failure_count": len(failures), "effective_cycle_count": len(cycles)}
    return {
        "schema_version": 1, "analysis_kind": "maven-dependency-graph-reconciliation",
        "inputs": {"declared_analysis_kind": declared["analysis_kind"],
                   "effective_analysis_kind": effective["analysis_kind"],
                   "effective_environment": effective.get("environment", {})},
        "summary": summary,
        "matches": sorted(matches, key=lambda x: (x["source_path"], x["group_id"], x["artifact_id"], x["declaration_context"])),
        "declared_only": sorted(declared_only, key=lambda x: (x["declaration"].get("source_path", ""),
            x["declaration"].get("group_id", ""), x["declaration"].get("artifact_id", ""))),
        "effective_only": sorted(effective_only, key=lambda x: (x["edge"].get("source_path", ""),
            x["edge"].get("group_id", ""), x["edge"].get("artifact_id", ""))),
        "effective_cycles": cycles, "effective_evaluation_failures": failures,
        "limitations": [
            "Matching uses source path, groupId, artifactId, type and classifier; version and scope are compared after matching.",
            "Missing profile declarations are not proof that a profile is obsolete; activation causes are not inferred.",
            "Effective-only edges are not attributed to inheritance, activated profiles or scanner coverage.",
            "The effective graph contains direct effective dependencies, not transitive runtime classpaths.",
            "Cycle results inherit the effective graph's configured scopes and project coverage.",
            "Inputs should describe the same repository revision and compatible Maven environments."]}

def write_outputs(result: dict[str, Any], output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    (output / "maven-dependency-reconciliation.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    fields = ["source_path", "source", "group_id", "artifact_id", "type", "classifier",
        "declaration_context", "declared_version", "effective_version", "version_changed",
        "version_managed_or_inherited", "declared_scope", "effective_scope", "scope_changed",
        "effective_target", "effective_target_paths", "effective_resolution"]
    with (output / "maven-dependency-reconciliation.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields); writer.writeheader()
        for item in result["matches"]:
            row = dict(item); row["effective_target_paths"] = ";".join(row["effective_target_paths"])
            writer.writerow(row)
    labels = [
        ("declared_project_count","Projects in declared graph"),("effective_project_count","Projects in effective graph"),
        ("matched_declaration_count","Matched declarations"),("version_changed_count","Version changes"),
        ("version_managed_or_inherited_count","Versions supplied by Maven"),("scope_changed_count","Scope changes"),
        ("declared_not_effective_count","Project declarations absent from effective model"),
        ("profile_not_effective_count","Profile declarations absent from effective model"),
        ("effective_only_count","Effective-only edges (origin unattributed)"),
        ("effective_unmatched_internal_count","Unmatched internal effective edges"),
        ("effective_evaluation_failure_count","Effective-POM evaluation failures"),
        ("effective_cycle_count","Effective dependency cycles (SCCs)")]
    lines = ["# Maven dependency graph reconciliation","","## Summary","","| Measure | Count |","|---|---:|"]
    lines += [f"| {label} | {result['summary'][name]} |" for name,label in labels]
    lines += ["","## Interpretation","",
        "- Version changes commonly reflect dependency management or inheritance; they are not automatically defects.",
        "- A profile declaration absent from this effective model was not present under the supplied Maven environment.",
        "- Effective-only edges are deliberately unattributed; confirm provenance before remediation.",
        "- Review cycles with their scopes and module boundaries; test/provided/runtime edges differ in significance.",
        "","## Effective cycles",""]
    lines += [f"- {', '.join(c)}" for c in result["effective_cycles"]] or ["No cycles reported by the effective input graph."]
    lines += ["","## Evaluation failures",""]
    lines += [f"- {f.get('pom','unknown')}: {f.get('error', f.get('stderr','unknown failure'))}"
              for f in result["effective_evaluation_failures"]] or ["No effective-POM evaluation failures recorded."]
    lines += ["","## Limitations",""] + [f"- {x}" for x in result["limitations"]]
    (output / "maven-dependency-reconciliation.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--declared", required=True, type=Path)
    parser.add_argument("--effective", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    try:
        result = reconcile(load_graph(args.declared, DECLARED_KIND), load_graph(args.effective, EFFECTIVE_KIND))
        write_outputs(result, args.output_dir)
    except (ValueError, OSError) as exc:
        print(str(exc), file=sys.stderr); return 2
    print(json.dumps(result["summary"], indent=2, sort_keys=True))
    print(f"Output: {args.output_dir.resolve()}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
