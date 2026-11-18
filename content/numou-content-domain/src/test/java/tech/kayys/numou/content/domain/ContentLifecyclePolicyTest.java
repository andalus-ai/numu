package tech.kayys.numou.content.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContentLifecyclePolicyTest {
 @Test void allowsExpectedEditorialTransitions(){assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.DRAFT,ContentStatus.IN_REVIEW));assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.APPROVED,ContentStatus.SCHEDULED));assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.PUBLISHED,ContentStatus.UNPUBLISHED));assertTrue(ContentLifecyclePolicy.canTransition(ContentStatus.ARCHIVED,ContentStatus.DRAFT));}
 @Test void rejectsSkippedOrBackwardTransitions(){assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.DRAFT,ContentStatus.PUBLISHED));assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.PUBLISHED,ContentStatus.APPROVED));assertFalse(ContentLifecyclePolicy.canTransition(ContentStatus.ARCHIVED,ContentStatus.PUBLISHED));}
}
