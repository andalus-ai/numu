"""Tests for Maven effective dependency graph parsing and cycle analysis."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parents[2] / "scripts" / "architecture" / "maven-effective-dependency-graph.py"
SPEC = importlib.util.spec_from_file_location("maven_effective_dependency_graph", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


class EffectivePomGraphTest(unittest.TestCase):
    def test_reads_effective_dependencies_with_managed_version_and_scope(self):
        xml = """<project xmlns="http://maven.apache.org/POM/4.0.0">
          <groupId>tech.kayys.numou</groupId><artifactId>service</artifactId><version>1</version>
          <dependencies><dependency><groupId>tech.kayys.numou</groupId>
          <artifactId>foundation</artifactId><version>3.2</version><scope>runtime</scope>
          </dependency></dependencies></project>"""
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "effective.xml"
            path.write_text(xml, encoding="utf-8")
            result = MODULE.read_effective_pom(path)
        self.assertEqual(result["group_id"], "tech.kayys.numou")
        self.assertEqual(result["dependencies"][0]["version"], "3.2")
        self.assertEqual(result["dependencies"][0]["scope"], "runtime")

    def test_reports_only_multi_node_and_self_cycles(self):
        cycles = MODULE.scc({"a", "b", "c"}, [("a", "b"), ("b", "a"), ("c", "c")])
        self.assertEqual(cycles, [["a", "b"], ["c"]])


if __name__ == "__main__":
    unittest.main()
