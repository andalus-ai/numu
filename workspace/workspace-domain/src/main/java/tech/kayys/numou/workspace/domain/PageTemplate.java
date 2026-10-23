package tech.kayys.numou.workspace.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Reusable, tenant-owned page blueprint. Template blocks are copied when instantiated. */
public record PageTemplate(UUID id, UUID tenantId, UUID workspaceId, UUID createdBy,
                           String name, String title, List<PageBlock> blocks,
                           Instant createdAt, Instant updatedAt) {
    public PageTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(createdBy, "createdBy");
        name = required(name, "name");
        title = required(title, "title");
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Set<Integer> positions = new HashSet<>();
        for (PageBlock block : blocks) {
            if (!positions.add(block.position())) throw new IllegalArgumentException("duplicate template block position");
        }
    }

    public static PageTemplate create(UUID tenantId, UUID workspaceId, UUID actorId,
                                      String name, String title, List<PageBlock> blocks, Instant now) {
        return new PageTemplate(UUID.randomUUID(), tenantId, workspaceId, actorId, name, title,
                normalize(blocks), now, now);
    }

    /** Produces fresh block IDs so instantiated pages never share block identity with the template. */
    public List<PageBlock> instantiateBlocks() {
        return normalize(blocks).stream()
                .map(b -> new PageBlock(UUID.randomUUID(), b.type(), b.text(), b.position(), b.attributes()))
                .toList();
    }

    private static List<PageBlock> normalize(List<PageBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return List.of();
        List<PageBlock> ordered = blocks.stream()
                .sorted(java.util.Comparator.comparingInt(PageBlock::position)).toList();
        java.util.ArrayList<PageBlock> normalized = new java.util.ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            PageBlock block = ordered.get(i);
            normalized.add(new PageBlock(block.id(), block.type(), block.text(), i, block.attributes()));
        }
        return List.copyOf(normalized);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        value = value.trim();
        if (value.length() > 200) throw new IllegalArgumentException(field + " must be at most 200 characters");
        return value;
    }
}
