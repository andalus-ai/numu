"""Unit tests for the repository Maven dependency graph analyzer."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parents[2] / "scripts" / "architecture" / "maven-dependency-graph.py"
SPEC = importlib.util.spec_from_file_location("maven_dependency_graph", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(MODULE)


def write_pom(root: Path, relative: str, body: str) -> Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding="utf-8")
    return path


class MavenDependencyGraphTest(unittest.TestCase):
    def test_resolves_local_properties_and_keeps_scope(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pom = write_pom(root, "a/pom.xml", """<project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion><groupId>tech.kayys.numou</groupId>
              <artifactId>a</artifactId><version>1</version>
              <properties><target.version>2</target.version></properties>
              <dependencies><dependency><groupId>tech.kayys.numou</groupId>
              <artifactId>b</artifactId><version>${target.version}</version>
              <scope>runtime</scope></dependency></dependencies>
            </project>""")
            project = MODULE.parse_pom(pom, root)
            dep = MODULE.dependencies(project)[0]
            self.assertEqual(dep["version"], "2")
            self.assertEqual(dep["scope"], "runtime")
            self.assertEqual(dep["declaration_context"], "project")

    def test_profile_dependencies_are_labeled_not_assumed_active(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pom = write_pom(root, "a/pom.xml", """<project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion><groupId>g</groupId>
              <artifactId>a</artifactId><version>1</version>
              <profiles><profile><id>optional</id><dependencies><dependency>
              <groupId>x</groupId><artifactId>y</artifactId><version>1</version>
              </dependency></dependencies></profile></profiles>
            </project>""")
            project = MODULE.parse_pom(pom, root)
            dep = MODULE.dependencies(project)[0]
            self.assertEqual(dep["declaration_context"], "profile:optional:activeByDefault=false")

    def test_dependency_management_is_not_counted_as_direct_edge(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            pom = write_pom(root, "a/pom.xml", """<project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion><groupId>g</groupId>
              <artifactId>a</artifactId><version>1</version>
              <dependencyManagement><dependencies><dependency><groupId>x</groupId>
              <artifactId>managed</artifactId><version>1</version></dependency>
              </dependencies></dependencyManagement>
            </project>""")
            project = MODULE.parse_pom(pom, root)
            self.assertEqual(MODULE.dependencies(project), [])

    def test_tarjan_reports_multi_node_and_self_cycles(self):
        nodes = {"a", "b", "c"}
        cycles = MODULE.strongly_connected_components(nodes, [("a", "b"), ("b", "a"), ("c", "c")])
        self.assertEqual(cycles, [["a", "b"], ["c"]])


if __name__ == "__main__":
    unittest.main()
