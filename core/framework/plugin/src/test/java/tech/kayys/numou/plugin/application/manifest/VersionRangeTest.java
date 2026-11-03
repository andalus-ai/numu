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
 @Test void unsupportedRangesAreRejected() {
  assertThatThrownBy(() -> VersionRange.parse(">=1.0.0")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> SemanticVersion.parse("01.0.0")).isInstanceOf(IllegalArgumentException.class);
 }
}
