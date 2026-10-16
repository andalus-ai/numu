package tech.kayys.syirkah.workforce.domain.compensation;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.workforce.domain.compensation.event.CompensationTermsCreated;
import tech.kayys.syirkah.workforce.domain.compensation.event.CompensationTermsEnded;
import tech.kayys.syirkah.workforce.domain.employment.EmploymentId;
import tech.kayys.syirkah.workforce.domain.paycomponent.PayComponentId;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompensationTermsTest {

    private static final EmploymentId EMPLOYMENT = EmploymentId.generate();
    private static final Currency IDR = Currency.of("IDR");
    private static final Money BASE_PAY = Money.of(BigDecimal.valueOf(10_000_000), IDR);
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    @Test
    void create_validTerms_setsFieldsAndRaisesEvent() {
        CompensationTerms terms = CompensationTerms.create(
                CompensationTermsId.generate(),
                EMPLOYMENT,
                BASE_PAY,
                PayFrequency.MONTHLY,
                START
        );

        assertThat(terms.status()).isEqualTo(CompensationTermsStatus.ACTIVE);
        assertThat(terms.basePay()).isEqualTo(BASE_PAY);
        assertThat(terms.payFrequency()).isEqualTo(PayFrequency.MONTHLY);
        assertThat(terms.effectiveFrom()).isEqualTo(START);
        assertThat(terms.effectiveTo()).isNull();
        assertThat(terms.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(CompensationTermsCreated.class);
    }

    @Test
    void create_negativeBasePay_throwsException() {
        Money negative = Money.of(BigDecimal.valueOf(-1000), IDR);

        assertThatThrownBy(() -> CompensationTerms.create(
                CompensationTermsId.generate(), EMPLOYMENT, negative, PayFrequency.MONTHLY, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addAndRemoveComponent_worksAsExpected() {
        CompensationTerms terms = CompensationTerms.create(
                CompensationTermsId.generate(), EMPLOYMENT, BASE_PAY, PayFrequency.MONTHLY, START);

        PayComponentId compId = PayComponentId.generate();
        CompensationComponent comp = new CompensationComponent(
                compId, Money.of(BigDecimal.valueOf(500_000), IDR), CompensationComponentType.EARNING);

        terms.addComponent(comp);
        assertThat(terms.components()).hasSize(1);
        assertThat(terms.components().get(0).amount()).isEqualTo(Money.of(BigDecimal.valueOf(500_000), IDR));

        terms.removeComponent(compId);
        assertThat(terms.components()).isEmpty();
    }

    @Test
    void end_validEndDate_endsTermsAndRaisesEvent() {
        CompensationTerms terms = CompensationTerms.create(
                CompensationTermsId.generate(), EMPLOYMENT, BASE_PAY, PayFrequency.MONTHLY, START);
        terms.pullDomainEvents();

        LocalDate end = LocalDate.of(2026, 6, 30);
        terms.end(end);

        assertThat(terms.status()).isEqualTo(CompensationTermsStatus.ENDED);
        assertThat(terms.effectiveTo()).isEqualTo(end);
        assertThat(terms.pullDomainEvents()).hasSize(1)
                .first().isInstanceOf(CompensationTermsEnded.class);
    }

    @Test
    void end_endDateBeforeEffectiveFrom_throwsException() {
        CompensationTerms terms = CompensationTerms.create(
                CompensationTermsId.generate(), EMPLOYMENT, BASE_PAY, PayFrequency.MONTHLY, START);

        assertThatThrownBy(() -> terms.end(LocalDate.of(2025, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isEffectiveOn_checksCorrectRange() {
        CompensationTerms terms = CompensationTerms.create(
                CompensationTermsId.generate(), EMPLOYMENT, BASE_PAY, PayFrequency.MONTHLY, START);

        assertThat(terms.isEffectiveOn(LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(terms.isEffectiveOn(START)).isTrue();
        assertThat(terms.isEffectiveOn(LocalDate.of(2026, 6, 1))).isTrue();

        terms.end(LocalDate.of(2026, 6, 30));
        assertThat(terms.isEffectiveOn(LocalDate.of(2026, 6, 30))).isFalse(); // Status is ENDED
    }
}
