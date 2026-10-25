import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("verify-capability-boundaries.py")
SPEC = importlib.util.spec_from_file_location("verify_capability_boundaries", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

RULES = {
    "version": 1,
    "rules": [{
        "id": "project-core-must-not-depend-on-construction",
        "consumer_artifact_prefixes": ["numou-project-"],
        "forbidden_dependency_artifact_prefixes": ["numou-construction-"],
        "forbidden_dependency_group": "tech.kayys.numou",
        "rationale": "neutral project core must not depend on construction",
    }],
}


def write_pom(root, relative, artifact, dependencies=""):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        '<?xml version="1.0"?><project xmlns="http://maven.apache.org/POM/4.0.0">'
        '<modelVersion>4.0.0</modelVersion>'
        f'<artifactId>{artifact}</artifactId><dependencies>{dependencies}</dependencies>'
        '</project>',
        encoding="utf-8",
    )


class CapabilityBoundaryVerifierTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.rules = self.root / "rules.json"
        self.rules.write_text(json.dumps(RULES), encoding="utf-8")

    def tearDown(self):
        self.temp.cleanup()

    def test_rejects_neutral_project_dependency_on_construction(self):
        write_pom(self.root, "project/project-domain/pom.xml", "numou-project-domain",
                  '<dependency><groupId>tech.kayys.numou</groupId>'
                  '<artifactId>numou-construction-domain</artifactId></dependency>')
        checked, errors = MODULE.scan(self.root, self.rules)
        self.assertEqual(1, checked)
        self.assertEqual(1, len(errors))
        self.assertIn("neutral project core must not depend on construction", errors[0])

    def test_allows_construction_to_depend_on_neutral_project(self):
        write_pom(self.root, "construction/construction-domain/pom.xml",
                  "numou-construction-domain",
                  '<dependency><groupId>tech.kayys.numou</groupId>'
                  '<artifactId>numou-project-domain</artifactId></dependency>')
        checked, errors = MODULE.scan(self.root, self.rules)
        self.assertEqual(1, checked)
        self.assertEqual([], errors)

    def test_allows_test_scoped_reference_to_construction(self):
        write_pom(self.root, "project/project-domain/pom.xml", "numou-project-domain",
                  '<dependency><groupId>tech.kayys.numou</groupId>'
                  '<artifactId>numou-construction-domain</artifactId>'
                  '<scope>test</scope></dependency>')
        _, errors = MODULE.scan(self.root, self.rules)
        self.assertEqual([], errors)

    def test_rejects_malformed_pom(self):
        path = self.root / "project/project-domain/pom.xml"
        path.parent.mkdir(parents=True)
        path.write_text("<project>", encoding="utf-8")
        _, errors = MODULE.scan(self.root, self.rules)
        self.assertTrue(any("cannot parse POM" in error for error in errors))

    def test_rejects_invalid_rule_configuration(self):
        self.rules.write_text('{"version": 1, "rules": []}', encoding="utf-8")
        _, errors = MODULE.scan(self.root, self.rules)
        self.assertTrue(any("must contain rules" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
