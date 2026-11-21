#!/usr/bin/env python3
"""Build a heuristic, explicitly unverified architecture review backlog."""
from __future__ import annotations
import argparse, csv, json, sys
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

def load(path: Path) -> dict[str, Any]:
    try:
        data=json.loads(path.read_text(encoding="utf-8"))
    except (OSError,json.JSONDecodeError) as exc:
        raise ValueError(f"cannot read {path}: {exc}") from exc
    if data.get("analysis_kind")!="maven-dependency-graph-reconciliation":
        raise ValueError("input must be a maven-dependency-graph-reconciliation report")
    for field in ("summary","matches","declared_only","effective_only","effective_cycles"):
        if field not in data: raise ValueError(f"reconciliation input missing {field}")
    return data

def build_backlog(data: dict[str, Any], fanout_threshold: int=12) -> dict[str, Any]:
    items=[]
    def add(key, category, priority, severity, confidence, subject, evidence, action):
        items.append({"id":key,"category":category,"priority":priority,"severity":severity,
            "detection_confidence":confidence,"status":"needs-human-validation",
            "subject":subject,"evidence":evidence,"recommended_investigation":action,
            "confirmed_violation":False})
    for i,cycle in enumerate(data["effective_cycles"],1):
        add(f"DEP-CYCLE-{i:03d}","dependency-cycle",95,"high","high",
            "Effective dependency cycle",
            {"members":cycle},
            "Inspect each edge and capability owner; determine whether this is a compile/runtime production cycle, test-only coupling, or an intentional composition boundary. Break only if it violates the documented dependency direction.")
    for i,item in enumerate(data["declared_only"],1):
        dep=item["declaration"]; cls=item["classification"]
        if dep.get("resolution")=="unmatched-internal":
            add(f"DEP-UNMATCHED-DECL-{i:04d}","unmatched-internal-declaration",90,"high","high",
                f"Unmatched internal declaration {dep.get('group_id')}:{dep.get('artifact_id')}",
                {"source_path":dep.get("source_path"),"declaration_context":dep.get("declaration_context"),
                 "version":dep.get("version"),"resolution":dep.get("resolution"),"classification":cls},
                "Check whether the target module is outside the scan, version/coordinate mismatch, stale dependency, or a missing artifact. Do not remove until consumers and build packaging are checked.")
    for i,item in enumerate(data["effective_only"],1):
        edge=item["edge"]
        if edge.get("resolution")=="unmatched-internal":
            add(f"DEP-UNMATCHED-EFF-{i:04d}","unmatched-internal-effective-edge",90,"high","high",
                f"Unmatched effective internal edge {edge.get('group_id')}:{edge.get('artifact_id')}",
                {"source_path":edge.get("source_path"),"target":edge.get("target"),
                 "scope":edge.get("scope"),"resolution":edge.get("resolution")},
                "Locate the artifact in the repository and validate groupId/version, reactor inclusion, profiles and build packaging.")
        else:
            add(f"DEP-EFFECTIVE-ONLY-{i:04d}","effective-only-edge",55,"medium","medium",
                f"Effective-only dependency {edge.get('group_id')}:{edge.get('artifact_id')}",
                {"source_path":edge.get("source_path"),"target":edge.get("target"),
                 "scope":edge.get("scope"),"resolution":edge.get("resolution")},
                "Trace effective-POM origin using verbose Maven origin comments and parent/profile declarations. Confirm whether the dependency is intended and whether its owner contract is public.")
    for i,item in enumerate(data["declared_only"],1):
        dep=item["declaration"]
        if item["classification"]=="declared-not-in-effective-model":
            add(f"DEP-DECL-NOT-EFF-{i:04d}","declaration-model-mismatch",75,"medium","medium",
                f"Project declaration absent from effective model: {dep.get('group_id')}:{dep.get('artifact_id')}",
                {"source_path":dep.get("source_path"),"declaration_context":dep.get("declaration_context"),
                 "version":dep.get("version"),"resolution":dep.get("resolution")},
                "Confirm both graph inputs use the same commit; inspect Maven model interpolation, exclusions and scanner parsing before changing the POM.")
    # High compile fan-out is a review signal, not proof of a bad composition root.
    compile_by_source=Counter()
    for match in data["matches"]:
        if match.get("effective_scope")=="compile":
            compile_by_source[match.get("source_path","")]+=1
    for path,count in sorted(compile_by_source.items()):
        if count>=fanout_threshold:
            add("DEP-FANOUT-"+str(len(items)+1).zfill(4),"high-dependency-fanout",60,"medium","medium",
                f"High compile dependency fan-out ({count})",
                {"source_path":path,"compile_dependency_count":count,"threshold":fanout_threshold},
                "Decide whether this is a legitimate application composition root. If not, identify cohesive use cases and public application ports; avoid splitting solely to reduce a count.")
    for i,match in enumerate(data["matches"],1):
        if match.get("version_changed"):
            add(f"DEP-VERSION-{i:04d}","managed-version-difference",35,"low","high",
                f"Maven-resolved version differs for {match.get('group_id')}:{match.get('artifact_id')}",
                {"source_path":match.get("source_path"),"declared_version":match.get("declared_version"),
                 "effective_version":match.get("effective_version")},
                "Review the managing parent/BOM and compatibility policy. A managed version change is expected in many builds.")
        if match.get("scope_changed"):
            add(f"DEP-SCOPE-{i:04d}","managed-scope-difference",50,"medium","high",
                f"Maven-resolved scope differs for {match.get('group_id')}:{match.get('artifact_id')}",
                {"source_path":match.get("source_path"),"declared_scope":match.get("declared_scope"),
                 "effective_scope":match.get("effective_scope")},
                "Inspect dependencyManagement and inherited declarations; verify runtime packaging and test isolation.")
    items.sort(key=lambda x:(-x["priority"],x["category"],x["id"]))
    for index,item in enumerate(items,1): item["rank"]=index
    counts=Counter(x["category"] for x in items)
    return {"schema_version":1,"analysis_kind":"maven-architecture-remediation-backlog",
        "method":{"fanout_threshold":fanout_threshold,"priority_is_heuristic":True,
            "all_items_require_human_validation":True,
            "confirmed_violation_count":0,
            "input_reconciliation_summary":data["summary"]},
        "summary":{"item_count":len(items),"category_counts":dict(sorted(counts.items())),
            "high_priority_count":sum(x["priority"]>=80 for x in items),
            "effective_cycle_count":len(data["effective_cycles"])},
        "items":items,
        "triage_guidance":[
            "Treat priority as investigation order, not proof of defect or an instruction to refactor.",
            "Confirm source and effective graphs refer to the same repository commit and Maven environment.",
            "Read AGENTS.md, ADR-003, the domain capability matrix and relevant capability contracts before proposing changes.",
            "For each candidate, record owner, public contract, affected consumers, data/persistence impact, tenant/security impact, deployment impact and compatibility plan.",
            "Only mark a finding confirmed after source-level evidence demonstrates a violation of an explicit architecture rule."]}

def write(data:dict[str,Any],out:Path)->None:
    out.mkdir(parents=True,exist_ok=True)
    (out/"maven-architecture-remediation-backlog.json").write_text(json.dumps(data,indent=2,sort_keys=True)+"\n",encoding="utf-8")
    fields=["rank","id","priority","severity","category","detection_confidence","status","confirmed_violation","subject","evidence","recommended_investigation"]
    with (out/"maven-architecture-remediation-backlog.csv").open("w",newline="",encoding="utf-8") as f:
        w=csv.DictWriter(f,fieldnames=fields);w.writeheader()
        for item in data["items"]:
            row=dict(item);row["evidence"]=json.dumps(row["evidence"],sort_keys=True)
            w.writerow({k:row.get(k) for k in fields})
    lines=["# Maven architecture remediation backlog","","This is a heuristic investigation queue. No item is automatically considered a confirmed architecture violation.","",
        "## Summary","",f"- Items: {data['summary']['item_count']}",f"- High priority (80+): {data['summary']['high_priority_count']}",
        f"- Effective dependency cycles: {data['summary']['effective_cycle_count']}","","## Ranked review queue","",
        "| Rank | Priority | Severity | Category | Subject | Status |","|---:|---:|---|---|---|---|"]
    for x in data["items"]:
        subject=x["subject"].replace("|","\\|")
        lines.append(f"| {x['rank']} | {x['priority']} | {x['severity']} | {x['category']} | {subject} | {x['status']} |")
    lines += ["","## Investigation detail",""]
    for x in data["items"]:
        lines += [f"### {x['rank']}. {x['subject']}","",
            f"- **ID:** {x['id']}","- **Priority:** {x['priority']} (heuristic)",
            f"- **Detection confidence:** {x['detection_confidence']}",
            f"- **Evidence:** `{json.dumps(x['evidence'],sort_keys=True)}`",
            f"- **Next action:** {x['recommended_investigation']}","- **Confirmed violation:** No",""]
    lines += ["## Triage rules",""]+[f"- {g}" for g in data["triage_guidance"]]
    (out/"maven-architecture-remediation-backlog.md").write_text("\n".join(lines)+"\n",encoding="utf-8")

def main()->int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--reconciliation",required=True,type=Path)
    p.add_argument("--output-dir",required=True,type=Path)
    p.add_argument("--fanout-threshold",type=int,default=12)
    a=p.parse_args()
    if a.fanout_threshold<1: p.error("--fanout-threshold must be positive")
    try: result=build_backlog(load(a.reconciliation),a.fanout_threshold);write(result,a.output_dir)
    except (ValueError,OSError) as exc: print(str(exc),file=sys.stderr);return 2
    print(json.dumps(result["summary"],indent=2,sort_keys=True));print(f"Output: {a.output_dir.resolve()}")
    return 0
if __name__=="__main__": raise SystemExit(main())
