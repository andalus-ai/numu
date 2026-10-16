package tech.kayys.syirkah.product.domain.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.BundleActivated;
import tech.kayys.syirkah.product.domain.event.BundleArchived;
import tech.kayys.syirkah.product.domain.event.BundleComponentAdded;
import tech.kayys.syirkah.product.domain.event.BundleComponentRemoved;
import tech.kayys.syirkah.product.domain.event.BundleCreated;
import tech.kayys.syirkah.product.domain.event.BundleDiscontinued;
import tech.kayys.syirkah.product.domain.product.ProductId;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bundle aggregate")
class BundleTest {

    private static Bundle newBundle() {
        return Bundle.create(
                BundleId.generate(),
                "COFFEE-COMBO",
                "Coffee Combo"
        );
    }

    private static ProductId coffeeId() {
        return ProductId.generate();
    }

    @Test
    void createsBundleInDraftState() {
        BundleId id = BundleId.generate();

        var bundle = Bundle.create(
                id,
                "COFFEE-COMBO",
                "Coffee Combo"
        );

        assertEquals(id, bundle.id());
        assertEquals(BundleStatus.DRAFT, bundle.status());
        assertEquals("COFFEE-COMBO", bundle.code());
        assertEquals("Coffee Combo", bundle.name());
        assertTrue(bundle.components().isEmpty());
    }

    @Test
    void creationRaisesBundleCreated() {
        var bundle = newBundle();

        var events = bundle.pullDomainEvents();

        assertEquals(1, events.size());

        var created = assertInstanceOf(
                BundleCreated.class,
                events.getFirst()
        );

        assertEquals(bundle.id(), created.bundleId());
        assertEquals("COFFEE-COMBO", created.code());
        assertEquals("product.bundle-created", created.eventType());
    }

    @Test
    void pullsAndClearsEvents() {
        var bundle = newBundle();

        bundle.pullDomainEvents();

        assertTrue(bundle.pullDomainEvents().isEmpty());
    }

    @Test
    void rejectsActivatingEmptyBundle() {
        var bundle = newBundle();

        assertThrows(BusinessRuleViolation.class, bundle::activate);
    }

    @Test
    void activatesDraftBundleWithComponent() {
        var bundle = newBundle();
        var productId = coffeeId();

        bundle.addComponent(productId, BigDecimal.ONE);
        bundle.pullDomainEvents();
        bundle.activate();

        assertEquals(BundleStatus.ACTIVE, bundle.status());

        var events = bundle.pullDomainEvents();

        assertEquals(1, events.size());
        assertInstanceOf(BundleActivated.class, events.getFirst());
    }

    @Test
    void rejectsActivatingTwice() {
        var bundle = newBundle();

        bundle.addComponent(coffeeId(), BigDecimal.ONE);
        bundle.activate();

        assertThrows(InvalidStateException.class, bundle::activate);
    }

    @Test
    void rejectsDiscontinuingDraftBundle() {
        var bundle = newBundle();

        assertThrows(
                InvalidStateException.class,
                bundle::discontinue
        );
    }

    @Test
    void rejectsArchivingActiveBundle() {
        var bundle = newBundle();

        bundle.addComponent(coffeeId(), BigDecimal.ONE);
        bundle.activate();

        assertThrows(InvalidStateException.class, bundle::archive);
    }

    @Test
    void followsFullLifecycleWithEvents() {
        var bundle = newBundle();

        bundle.addComponent(coffeeId(), BigDecimal.ONE);
        bundle.pullDomainEvents();

        bundle.activate();
        bundle.discontinue();
        bundle.archive();

        assertEquals(BundleStatus.ARCHIVED, bundle.status());

        var events = bundle.pullDomainEvents();

        assertEquals(3, events.size());
        assertInstanceOf(BundleActivated.class, events.get(0));
        assertInstanceOf(BundleDiscontinued.class, events.get(1));
        assertInstanceOf(BundleArchived.class, events.get(2));
    }

    @Test
    void addsAndRemovesComponents() {
        var bundle = newBundle();
        var productId = coffeeId();

        bundle.pullDomainEvents();

        bundle.addComponent(productId, new BigDecimal("2"));

        assertEquals(1, bundle.components().size());
        assertEquals(productId, bundle.components().getFirst().productId());
        assertEquals(
                new BigDecimal("2"),
                bundle.components().getFirst().quantity()
        );

        var added = assertInstanceOf(
                BundleComponentAdded.class,
                bundle.pullDomainEvents().getFirst()
        );
        assertEquals(productId, added.productId());

        bundle.removeComponent(productId);

        assertTrue(bundle.components().isEmpty());
        assertInstanceOf(
                BundleComponentRemoved.class,
                bundle.pullDomainEvents().getFirst()
        );
    }

    @Test
    void rejectsDuplicateComponent() {
        var bundle = newBundle();
        var productId = coffeeId();

        bundle.addComponent(productId, BigDecimal.ONE);

        assertThrows(
                BusinessRuleViolation.class,
                () -> bundle.addComponent(productId, BigDecimal.TEN)
        );
    }

    @Test
    void rejectsRemovingUnknownComponent() {
        var bundle = newBundle();

        assertThrows(
                BusinessRuleViolation.class,
                () -> bundle.removeComponent(coffeeId())
        );
    }

    @Test
    void activeBundleCannotBeModified() {
        var bundle = newBundle();
        var productId = coffeeId();

        bundle.addComponent(productId, BigDecimal.ONE);
        bundle.activate();

        assertThrows(
                InvalidStateException.class,
                () -> bundle.addComponent(
                        coffeeId(),
                        BigDecimal.ONE
                )
        );

        assertThrows(
                InvalidStateException.class,
                () -> bundle.removeComponent(productId)
        );
    }

    @Test
    void rejectsBlankCodeAndNullId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Bundle.create(
                        BundleId.generate(),
                        " ",
                        "Coffee Combo"
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> Bundle.create(
                        null,
                        "COFFEE-COMBO",
                        "Coffee Combo"
                )
        );
    }
}
