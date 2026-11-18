package tech.kayys.numou.plugin.application.manifest;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VersionRangeTest {
 @Test void semverPrecedenceAndBuildMetadataFollowSemver() {
  assertThat(SemanticVersion.parse("1.0.0-alpha.2")).isLessThan(SemanticVersion.parse("1.0.0-alpha.10"));
  assertThat(SemanticVersion.parse("1.0.0+one")).isEqualByComparingTo(SemanticVersion.parse("1.0.0+two"));
 }
 @Test void caretAndTildeRespectCompatibilityBoundaries() {
  assertThat(VersionRange.parse("^1.4.2").includes(SemanticVersion.parse("1.9.0"))).isTrue();
  assertThat(VersionRange.parse("^1.4.2").includes(SemanticVersion.parse("2.0.0"))).isFalse();
  assertThat(VersionRange.parse("^0.2.3").includes(SemanticVersion.parse("0.2.9"))).isTrue();
  assertThat(VersionRange.parse("^0.2.3").includes(SemanticVersion.parse("0.3.0"))).isFalse();
  assertThat(VersionRange.parse("^0.0.3").includes(SemanticVersion.parse("0.0.4"))).isFalse();
  assertThat(VersionRange.parse("~1.4.2").includes(SemanticVersion.parse("1.4.99"))).isTrue();
  assertThat(VersionRange.parse("~1.4.2").includes(SemanticVersion.parse("1.5.0"))).isFalse();
 }
 @Test void supportsUnboundedNumericComponentsWithoutOverflow() {
  var huge = SemanticVersion.parse("999999999999999999999999999999.0.0");
  assertThat(huge).isGreaterThan(SemanticVersion.parse("2147483647.999999999999999999.999999999999"));
  assertThat(VersionRange.parse("^999999999999999999999999999999.0.0")
      .includes(SemanticVersion.parse("1000000000000000000000000000000.0.0"))).isFalse();
  assertThat(VersionRange.parse("~1.999999999999999999999999.0")
      .includes(SemanticVersion.parse("1.1000000000000000000000000.0"))).isFalse();
 }
 @Test void prereleasesMustMatchTheRangeCoreTuple() {
  assertThat(VersionRange.parse("^1.2.3-alpha.1")
      .includes(SemanticVersion.parse("1.2.3-alpha.2"))).isTrue();
  assertThat(VersionRange.parse("^1.2.3-alpha.1")
      .includes(SemanticVersion.parse("1.2.4-beta.1"))).isFalse();
  assertThat(VersionRange.parse("^1.2.3")
      .includes(SemanticVersion.parse("1.2.3-rc.1"))).isFalse();
 }
 @Test void unsupportedRangesAreRejected() {
  assertThatThrownBy(() -> VersionRange.parse(">=1.0.0")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> SemanticVersion.parse("01.0.0")).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void semverPrereleasePrecedenceMatchesSpecificationSequence() {
  String[] ordered = {"1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta",
   "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0"};
  for (int i = 0; i < ordered.length - 1; i++)
   assertThat(SemanticVersion.parse(ordered[i])).isLessThan(SemanticVersion.parse(ordered[i + 1]));
 }
 @Test void rejectsEmptyAndLeadingZeroPrereleaseIdentifiers() {
  assertThatThrownBy(() -> SemanticVersion.parse("1.0.0-alpha..1")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> SemanticVersion.parse("1.0.0-01")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> SemanticVersion.parse("1.0.0-alpha+")).isInstanceOf(IllegalArgumentException.class);
 }
}
