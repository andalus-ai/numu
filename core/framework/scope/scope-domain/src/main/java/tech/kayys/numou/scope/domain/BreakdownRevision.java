package tech.kayys.numou.scope.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, tenant-scoped snapshot of one complete breakdown tree.
 *
 * <p>A revision is a full snapshot, not a patch. Its nodes must all belong to
 * the same structure, have exactly one root, and satisfy the structural tree
 * invariants. Persistence must enforce immutability after publication.</p>
 */
public record BreakdownRevision(
        TenantId tenantId,
        BreakdownStructureId structureId,
        BreakdownRevisionId revisionId,
        long revisionNumber,
        String contentHash,
        Instant createdAt,
        String createdBy,
        List<BreakdownNode> nodes) {

    public BreakdownRevision {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(revisionId, "Revision id cannot be null");
        if (revisionNumber < 1) {
            throw new IllegalArgumentException("Revision number must be positive");
        }
        contentHash = requireText(contentHash, "Content hash");
        if (!contentHash.matches("(?i)^sha256:[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("Content hash must use sha256:<64 hexadecimal characters>");
        }
        Objects.requireNonNull(createdAt, "Revision creation time cannot be null");
        if (createdBy != null && createdBy.isBlank()) {
            throw new IllegalArgumentException("Created-by value cannot be blank when supplied");
        }
        Objects.requireNonNull(nodes, "Revision nodes cannot be null");
        nodes = canonicalOrder(nodes);
        validateSnapshot(structureId, nodes);
    }

    private static List<BreakdownNode> canonicalOrder(List<BreakdownNode> source) {
        List<BreakdownNode> copy = new ArrayList<>(source);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Revision nodes cannot contain null");
        }
        copy.sort(Comparator
                .comparing((BreakdownNode node) -> node.parentId() == null ? "" : node.parentId().value().toString())
                .thenComparingInt(BreakdownNode::position)
                .thenComparing(node -> node.code().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(node -> node.id().value().toString()));
        return List.copyOf(copy);
    }

    private static void validateSnapshot(
            BreakdownStructureId structureId,
            List<BreakdownNode> nodes) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("A published WBS revision must contain at least one node");
        }
        if (nodes.stream().anyMatch(node -> !structureId.equals(node.structureId()))) {
            throw new IllegalArgumentException("Every revision node must belong to the revision structure");
        }

        List<BreakdownTreeValidator.Violation> violations = BreakdownTreeValidator.validate(nodes);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid WBS revision tree: " + violations);
        }

        long rootCount = nodes.stream().filter(BreakdownNode::isRoot).count();
        if (rootCount != 1) {
            throw new IllegalArgumentException("A WBS revision must contain exactly one root node");
        }

        Set<String> siblingPositions = new HashSet<>();
        for (BreakdownNode node : nodes) {
            String parent = node.parentId() == null ? "<root>" : node.parentId().value().toString();
            String key = parent + "|" + node.position();
            if (!siblingPositions.add(key)) {
                throw new IllegalArgumentException("Sibling node positions must be unique");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value.trim();
    }
}
