package tech.kayys.syirkah.product.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.application.support.InMemoryClassificationNodeRepository;
import tech.kayys.syirkah.product.application.support.InMemoryClassificationSchemeRepository;
import tech.kayys.syirkah.product.application.support.InMemoryProductClassificationRepository;
import tech.kayys.syirkah.product.application.support.InMemoryProductRepository;
import tech.kayys.syirkah.product.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.product.domain.classification.ClassificationNodeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeType;
import tech.kayys.syirkah.product.domain.event.ClassificationNodeCreated;
import tech.kayys.syirkah.product.domain.event.ClassificationSchemeCreated;
import tech.kayys.syirkah.product.domain.event.ProductClassified;
import tech.kayys.syirkah.product.domain.event.ProductDeclassified;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.product.ProductType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Classification command handlers")
class ClassificationCommandHandlerTest {

    private final InMemoryProductRepository products =
            new InMemoryProductRepository();
    private final InMemoryClassificationSchemeRepository schemes =
            new InMemoryClassificationSchemeRepository();
    private final InMemoryClassificationNodeRepository nodes =
            new InMemoryClassificationNodeRepository();
    private final InMemoryProductClassificationRepository classifications =
            new InMemoryProductClassificationRepository();
    private final RecordingEventPublisher events =
            new RecordingEventPublisher();

    private final CreateClassificationSchemeHandler createScheme =
            new CreateClassificationSchemeHandler(schemes, events);
    private final ActivateClassificationSchemeHandler activateScheme =
            new ActivateClassificationSchemeHandler(schemes, events);
    private final CreateClassificationNodeHandler createNode =
            new CreateClassificationNodeHandler(schemes, nodes, events);
    private final MoveClassificationNodeHandler moveNode =
            new MoveClassificationNodeHandler(nodes, events);
    private final ClassifyProductHandler classify =
            new ClassifyProductHandler(
                    products, schemes, nodes, classifications, events);
    private final DeclassifyProductHandler declassify =
            new DeclassifyProductHandler(classifications, events);

    private static String failureCode(Result<?> result) {
        return ((Result.Failure<?>) result).error().code();
    }

    private ClassificationSchemeId activeScheme(String code) {
        var schemeId = createScheme.handle(
                new CreateClassificationSchemeCommand(
                        code, code + " taxonomy", ClassificationSchemeType.RETAIL)
        ).await().indefinitely().orElseThrow();

        activateScheme.handle(
                new ActivateClassificationSchemeCommand(schemeId)
        ).await().indefinitely().orElseThrow();

        return schemeId;
    }

    private ClassificationNodeId node(
            ClassificationSchemeId schemeId,
            ClassificationNodeId parent,
            String code
    ) {
        return createNode.handle(
                new CreateClassificationNodeCommand(
                        schemeId, parent, code, code, 0)
        ).await().indefinitely().orElseThrow();
    }

    private ProductId newProduct(String code) {
        var product = Product.create(
                ProductId.generate(), code, code, null, ProductType.PHYSICAL);
        products.save(product).toCompletableFuture().join();
        return product.id();
    }

    @Test
    void createsSchemeAndPublishesEvent() {
        var schemeId = createScheme.handle(
                new CreateClassificationSchemeCommand(
                        "TAX", "Tax taxonomy", ClassificationSchemeType.TAX)
        ).await().indefinitely().orElseThrow();

        assertTrue(schemeId != null);
        assertInstanceOf(
                ClassificationSchemeCreated.class,
                events.published().getFirst()
        );
    }

    @Test
    void rejectsDuplicateSchemeCode() {
        createScheme.handle(new CreateClassificationSchemeCommand(
                "TAX", "Tax", ClassificationSchemeType.TAX))
                .await().indefinitely().orElseThrow();

        var result = createScheme.handle(new CreateClassificationSchemeCommand(
                "TAX", "Tax again", ClassificationSchemeType.TAX))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(
                "CLASSIFICATION_SCHEME_CODE_ALREADY_EXISTS",
                failureCode(result));
    }

    @Test
    void createsNodeUnderActiveSchemeAndPublishesEvent() {
        var schemeId = activeScheme("RETAIL");
        events.reset();

        node(schemeId, null, "BEVERAGES");

        assertInstanceOf(
                ClassificationNodeCreated.class,
                events.published().getFirst());
    }

    @Test
    void rejectsNodeWhenSchemeNotActive() {
        var schemeId = createScheme.handle(new CreateClassificationSchemeCommand(
                "RETAIL", "Retail", ClassificationSchemeType.RETAIL))
                .await().indefinitely().orElseThrow();

        var result = createNode.handle(new CreateClassificationNodeCommand(
                schemeId, null, "BEVERAGES", "Beverages", 0))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("CLASSIFICATION_SCHEME_NOT_ACTIVE", failureCode(result));
    }

    @Test
    void rejectsDuplicateNodeCodeWithinScheme() {
        var schemeId = activeScheme("RETAIL");
        node(schemeId, null, "BEVERAGES");

        var result = createNode.handle(new CreateClassificationNodeCommand(
                schemeId, null, "BEVERAGES", "Beverages again", 1))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(
                "CLASSIFICATION_NODE_CODE_ALREADY_EXISTS", failureCode(result));
    }

    @Test
    void rejectsNodeWithParentFromAnotherScheme() {
        var retail = activeScheme("RETAIL");
        var tax = activeScheme("TAX");
        var retailNode = node(retail, null, "BEVERAGES");

        var result = createNode.handle(new CreateClassificationNodeCommand(
                tax, retailNode, "COLA", "Cola", 0))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(
                "CLASSIFICATION_NODE_PARENT_WRONG_SCHEME", failureCode(result));
    }

    @Test
    void rejectsMoveThatWouldCreateCycle() {
        var schemeId = activeScheme("RETAIL");
        var a = node(schemeId, null, "A");
        var b = node(schemeId, a, "B");
        var c = node(schemeId, b, "C");

        var result = moveNode.handle(
                new MoveClassificationNodeCommand(a, c))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("CLASSIFICATION_NODE_CYCLE_DETECTED", failureCode(result));
    }

    @Test
    void allowsMoveToUnrelatedParent() {
        var schemeId = activeScheme("RETAIL");
        var a = node(schemeId, null, "A");
        var b = node(schemeId, null, "B");

        var moved = moveNode.handle(
                new MoveClassificationNodeCommand(b, a))
                .await().indefinitely();

        assertTrue(moved.isSuccess());
        assertEquals(
                a,
                nodes.findById(b).toCompletableFuture().join()
                        .orElseThrow().parentNodeId());
    }

    @Test
    void classifiesProductAndPublishesEvent() {
        var schemeId = activeScheme("RETAIL");
        var nodeId = node(schemeId, null, "COFFEE");
        var productId = newProduct("COFFEE-LATTE");
        events.reset();

        var classification = classify.handle(
                new ClassifyProductCommand(productId, schemeId, nodeId))
                .await().indefinitely().orElseThrow();

        assertEquals(productId, classification.productId());
        assertEquals(nodeId, classification.nodeId());
        assertInstanceOf(ProductClassified.class, events.published().getFirst());
    }

    @Test
    void rejectsClassifyWhenNodeBelongsToAnotherScheme() {
        var retail = activeScheme("RETAIL");
        var tax = activeScheme("TAX");
        var taxNode = node(tax, null, "CARBONATED");
        var productId = newProduct("COCA-COLA");

        var result = classify.handle(
                new ClassifyProductCommand(productId, retail, taxNode))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("CLASSIFICATION_NODE_WRONG_SCHEME", failureCode(result));
    }

    @Test
    void rejectsDuplicateClassification() {
        var schemeId = activeScheme("RETAIL");
        var nodeId = node(schemeId, null, "COFFEE");
        var productId = newProduct("COFFEE-LATTE");

        classify.handle(new ClassifyProductCommand(productId, schemeId, nodeId))
                .await().indefinitely().orElseThrow();

        var result = classify.handle(
                new ClassifyProductCommand(productId, schemeId, nodeId))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PRODUCT_ALREADY_CLASSIFIED", failureCode(result));
    }

    @Test
    void declassifiesProductAndPublishesEvent() {
        var schemeId = activeScheme("RETAIL");
        var nodeId = node(schemeId, null, "COFFEE");
        var productId = newProduct("COFFEE-LATTE");

        classify.handle(new ClassifyProductCommand(productId, schemeId, nodeId))
                .await().indefinitely().orElseThrow();
        events.reset();

        declassify.handle(
                new DeclassifyProductCommand(productId, schemeId, nodeId))
                .await().indefinitely().orElseThrow();

        assertInstanceOf(
                ProductDeclassified.class, events.published().getFirst());
        assertTrue(classifications.findByProductId(productId)
                .toCompletableFuture().join().isEmpty());
    }

    @Test
    void rejectsDeclassifyWhenNotClassified() {
        var schemeId = activeScheme("RETAIL");
        var nodeId = node(schemeId, null, "COFFEE");
        var productId = newProduct("COFFEE-LATTE");

        var result = declassify.handle(
                new DeclassifyProductCommand(productId, schemeId, nodeId))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PRODUCT_CLASSIFICATION_NOT_FOUND", failureCode(result));
    }
}
