package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the Sharia rule catalog honest. See {@link ShariaRuleCatalog}.
 */
class ShariaRuleCatalogTest {

    @Test
    void ruleNumbersAreOneToTwentySixInOrder() {
        List<ShariaRule> rules = ShariaRuleCatalog.all();
        assertEquals(26, rules.size());
        for (int i = 0; i < rules.size(); i++) {
            assertEquals(i + 1, rules.get(i).id());
        }
    }

    @Test
    void everyRuleIsEnforcedByACodeOrProvedByATest() {
        for (ShariaRule r : ShariaRuleCatalog.all()) {
            assertTrue(!r.codes().isEmpty() || !r.testedBy().isEmpty(), "rule " + r.id() + " has no evidence");
        }
    }

    @Test
    void everyTestClassNamedInTheCatalogExists() throws ClassNotFoundException {
        for (ShariaRule r : ShariaRuleCatalog.all()) {
            for (String t : r.testedBy()) {
                Class.forName("tech.kayys.numou.numoudomain." + t);
            }
        }
    }

    @Test
    void noCheckerEmitsACodeThatTheCatalogDoesNotDocument() {
        Set<String> undocumented = new TreeSet<>();
        for (String code : ConformanceScenarios.observedCodes()) {
            if (!ShariaRuleCatalog.covers(code)) {
                undocumented.add(code);
            }
        }
        assertEquals(Set.of(), undocumented, "codes emitted but not in the catalog");
    }

    @Test
    void everyCodeInTheCatalogIsProducedByTheConformanceScenarios() {
        Set<String> observed = ConformanceScenarios.observedCodes();
        Set<String> neverSeen = new TreeSet<>();
        for (ShariaRule r : ShariaRuleCatalog.all()) {
            for (String c : r.codes()) {
                boolean seen = c.endsWith("*")
                        ? observed.stream().anyMatch(o -> o.startsWith(c.substring(0, c.length() - 1)))
                        : observed.contains(c);
                if (!seen) {
                    neverSeen.add("rule " + r.id() + ": " + c);
                }
            }
        }
        assertEquals(Set.of(), neverSeen, "codes listed in the catalog that no scenario produced");
    }

    @Test
    void theGeneratedDocumentMatchesTheCatalog() throws IOException {
        Path doc = Path.of("..", "doc", "SHARIA-RULES.md");
        String expected = ShariaRuleCatalog.render();
        if (Boolean.getBoolean("sharia.rules.update")) {
            Files.writeString(doc, expected, StandardCharsets.UTF_8);
        }
        assertTrue(Files.exists(doc), "missing " + doc.toAbsolutePath() + "; run with -Dsharia.rules.update=true");
        assertEquals(expected, Files.readString(doc, StandardCharsets.UTF_8),
                "doc/SHARIA-RULES.md is out of date; regenerate with -Dsharia.rules.update=true and review the diff");
    }

    @Test
    void aFrozenCatalogHasAdviserSignOffNoPendingRulingsAndAnUnchangedFingerprint() {
        if (ShariaRuleCatalog.FROZEN_FINGERPRINT == null) {
            return; // still open for change
        }
        assertNotNull(ShariaRuleCatalog.ADVISER_SIGNOFF, "a frozen catalog needs the adviser's sign-off recorded");
        assertTrue(ShariaRuleCatalog.all().stream().noneMatch(r -> r.ruling() == ShariaRule.Ruling.PENDING_ADVISER),
                "a frozen catalog cannot have rules waiting for the adviser");
        assertEquals(ShariaRuleCatalog.FROZEN_FINGERPRINT, ShariaRuleCatalog.fingerprint(),
                "a frozen rule changed; this needs a new adviser sign-off and a new fingerprint");
    }
}
