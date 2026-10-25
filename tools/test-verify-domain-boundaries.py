import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("verify-domain-boundaries.py")
SPEC = importlib.util.spec_from_file_location("verify_domain_boundaries", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def write_pom(root, relative, dependencies):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        '<?xml version="1.0"?><project xmlns="http://maven.apache.org/POM/4.0.0">'
        '<modelVersion>4.0.0</modelVersion><dependencies>'
        + dependencies + '</dependencies></project>',
        encoding="utf-8",
    )


class DomainBoundaryVerifierTest(unittest.TestCase):
    def test_allows_domain_contract_and_test_dependencies(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_pom(root, "demo/demo-domain/pom.xml",
                '<dependency><groupId>tech.kayys.numou</groupId>'
                '<artifactId>numou-project-domain</artifactId></dependency>'
                '<dependency><groupId>org.springframework</groupId>'
                '<artifactId>spring-context</artifactId><scope>test</scope></dependency>')
            checked, errors = MODULE.scan(root)
            self.assertEqual(1, checked)
            self.assertEqual([], errors)

    def test_rejects_runtime_database_dependency(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_pom(root, "demo/demo-domain/pom.xml",
                '<dependency><groupId>org.postgresql</groupId>'
                '<artifactId>postgresql</artifactId></dependency>')
            checked, errors = MODULE.scan(root)
            self.assertEqual(1, checked)
            self.assertEqual(1, len(errors))
            self.assertIn("move infrastructure integration", errors[0])

    def test_rejects_invalid_pom(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / "demo/demo-domain/pom.xml"
            path.parent.mkdir(parents=True)
            path.write_text("<project>", encoding="utf-8")
            checked, errors = MODULE.scan(root)
            self.assertEqual(0, checked)
            self.assertTrue(any("cannot parse POM" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
