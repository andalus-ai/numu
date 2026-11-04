package tech.kayys.numou.developerplatform.domain;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
class DeveloperApplicationTest {
 private static final Instant NOW=Instant.parse("2026-11-04T00:00:00Z");
 @Test void normalizesSlugAndRequiresTenant(){var a=DeveloperApplication.register(TenantId.generate()," My-App ","My App",null,"user:1",NOW);assertThat(a.slug()).isEqualTo("my-app");assertThat(a.revision()).isEqualTo(1);}
 @Test void rejectsInvalidSlug(){assertThatThrownBy(()->DeveloperApplication.register(TenantId.generate(),"../admin","App","","user:1",NOW)).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejectedTransitionDoesNotPartiallyMutateAggregate(){
  var a=DeveloperApplication.register(TenantId.generate(),"atomic","Original","original description","user:1",NOW);
  assertThatThrownBy(()->a.updateProfile("Changed","changed"," ", "corr",NOW.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
  assertThat(a.name()).isEqualTo("Original");assertThat(a.description()).isEqualTo("original description");assertThat(a.revision()).isEqualTo(1);
  assertThatThrownBy(()->a.suspend("user:1","corr",NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
  assertThat(a.status()).isEqualTo(DeveloperApplicationStatus.ACTIVE);assertThat(a.revision()).isEqualTo(1);
 }
 @Test void updateAdvancesRevisionAndArchiveIsTerminal(){var a=DeveloperApplication.register(TenantId.generate(),"portal","Portal","","user:1",NOW);var e=a.updateProfile("Developer Portal","API integrations","user:1","corr-1",NOW.plusSeconds(1));assertThat(e.revision()).isEqualTo(2);a.archive("user:1","corr-1",NOW.plusSeconds(2));assertThatThrownBy(()->a.updateProfile("Other","","user:1","",NOW.plusSeconds(3))).isInstanceOf(IllegalStateException.class);}
}