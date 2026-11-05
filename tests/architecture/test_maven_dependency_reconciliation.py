import importlib.util, json, tempfile, unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[2] / "scripts/architecture/maven-dependency-reconciliation.py"
SPEC = importlib.util.spec_from_file_location("maven_reconcile", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)

def declaration(version="", scope="compile", context="project"):
    return {"source_path":"alpha/pom.xml","source":"g:a:1","group_id":"g","artifact_id":"a",
        "version":version,"scope":scope,"type":"jar","classifier":"",
        "declaration_context":context,"unresolved_properties":[]}

def effective(version="2", scope="compile", artifact="a"):
    return {"source_path":"alpha/pom.xml","source":"g:a:1","group_id":"g","artifact_id":artifact,
        "version":version,"scope":scope,"type":"jar","classifier":"","optional":False,
        "target":f"g:{artifact}:{version}","target_paths":[],"resolution":"external"}

class ReconciliationTest(unittest.TestCase):
    def setUp(self):
        self.d={"analysis_kind":MODULE.DECLARED_KIND,"projects":[{"path":"alpha/pom.xml"}],"dependencies":[]}
        self.e={"analysis_kind":MODULE.EFFECTIVE_KIND,"projects":[{"path":"alpha/pom.xml"}],
            "dependencies":[],"strongly_connected_components":[],"evaluation_failures":[],"unmatched_internal_count":0}
    def test_managed_version_and_scope_change(self):
        self.d["dependencies"]=[declaration()]
        self.e["dependencies"]=[effective(scope="runtime")]
        m=MODULE.reconcile(self.d,self.e)["matches"][0]
        self.assertTrue(m["version_managed_or_inherited"]); self.assertTrue(m["scope_changed"])
    def test_project_declaration_missing(self):
        self.d["dependencies"]=[declaration()]
        r=MODULE.reconcile(self.d,self.e)
        self.assertEqual(r["declared_only"][0]["classification"],"declared-not-in-effective-model")
    def test_profile_declaration_missing(self):
        self.d["dependencies"]=[declaration(context="profile:integration:activeByDefault=false")]
        r=MODULE.reconcile(self.d,self.e)
        self.assertEqual(r["declared_only"][0]["classification"],"profile-not-in-effective-model")
    def test_effective_only_is_unattributed(self):
        self.e["dependencies"]=[effective(artifact="parent-provided")]
        r=MODULE.reconcile(self.d,self.e)
        self.assertEqual(r["effective_only"][0]["classification"],"effective-only-origin-unattributed")
    def test_version_difference(self):
        self.d["dependencies"]=[declaration(version="1")]
        self.e["dependencies"]=[effective(version="2")]
        self.assertTrue(MODULE.reconcile(self.d,self.e)["matches"][0]["version_changed"])
    def test_rejects_wrong_schema_kind(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"graph.json"; p.write_text(json.dumps({"analysis_kind":"wrong","projects":[],"dependencies":[]}))
            with self.assertRaises(ValueError): MODULE.load_graph(p,MODULE.DECLARED_KIND)

if __name__ == "__main__": unittest.main()
