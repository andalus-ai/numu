import importlib.util, unittest
from pathlib import Path
SCRIPT=Path(__file__).resolve().parents[2]/"scripts/architecture/maven-architecture-remediation-backlog.py"
SPEC=importlib.util.spec_from_file_location("maven_backlog",SCRIPT)
MODULE=importlib.util.module_from_spec(SPEC);assert SPEC and SPEC.loader;SPEC.loader.exec_module(MODULE)

class BacklogTest(unittest.TestCase):
 def base(self):
  return {"analysis_kind":"maven-dependency-graph-reconciliation","summary":{},
   "matches":[],"declared_only":[],"effective_only":[],"effective_cycles":[]}
 def test_cycle_is_high_priority_but_not_confirmed(self):
  d=self.base();d["effective_cycles"]=[["g:a:1","g:b:1"]]
  item=MODULE.build_backlog(d)["items"][0]
  self.assertEqual(item["priority"],95);self.assertFalse(item["confirmed_violation"])
 def test_unmatched_internal_declaration(self):
  d=self.base();d["declared_only"]=[{"classification":"declared-not-in-effective-model",
   "declaration":{"resolution":"unmatched-internal","group_id":"g","artifact_id":"missing","source_path":"x/pom.xml"}}]
  self.assertEqual(MODULE.build_backlog(d)["items"][0]["category"],"unmatched-internal-declaration")
 def test_high_fanout_is_review_not_defect(self):
  d=self.base();d["matches"]=[{"source_path":"app/pom.xml","effective_scope":"compile"} for _ in range(3)]
  item=MODULE.build_backlog(d,fanout_threshold=3)["items"][0]
  self.assertEqual(item["category"],"high-dependency-fanout");self.assertFalse(item["confirmed_violation"])
 def test_scope_and_version_differences(self):
  d=self.base();d["matches"]=[{"source_path":"x/pom.xml","effective_scope":"runtime",
   "scope_changed":True,"version_changed":True,"group_id":"g","artifact_id":"a",
   "declared_scope":"compile","effective_scope":"runtime","declared_version":"1","effective_version":"2"}]
  categories={x["category"] for x in MODULE.build_backlog(d)["items"]}
  self.assertIn("managed-scope-difference",categories);self.assertIn("managed-version-difference",categories)

if __name__=="__main__":unittest.main()
