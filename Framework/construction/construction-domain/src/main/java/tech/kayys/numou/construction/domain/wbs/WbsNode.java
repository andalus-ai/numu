package tech.kayys.syirkah.construction.domain.wbs;

import tech.kayys.syirkah.construction.domain.wbs.event.WbsNodeCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class WbsNode extends AbstractAggregateRoot<WbsNodeId> {
    private final UUID projectId;
    private final UUID parentNodeId;
    private final String code;
    private String name;
    private WbsNodeType type;
    private UUID projectTaskId;

    private WbsNode(WbsNodeId id, UUID projectId, UUID parentNodeId, String code, String name, WbsNodeType type) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.code = Objects.requireNonNull(code, "WBS code cannot be blank");
        this.name = Objects.requireNonNull(name, "WBS name cannot be blank");
        this.type = Objects.requireNonNull(type, "WBS node type cannot be null");
        this.parentNodeId = parentNodeId;
    }

    public static WbsNode create(UUID projectId, UUID parentNodeId, String code, String name, WbsNodeType type) {
        var node = new WbsNode(WbsNodeId.generate(), projectId, parentNodeId, code, name, type);
        node.raise(new WbsNodeCreated(UUID.randomUUID(), Instant.now(), node.id().value(), projectId, code, type));
        return node;
    }

    public void linkTask(UUID projectTaskId) {
        if (type != WbsNodeType.WORK_PACKAGE && type != WbsNodeType.ACTIVITY) {
            throw new IllegalStateException("Only work packages and activities can link to tasks");
        }
        this.projectTaskId = Objects.requireNonNull(projectTaskId, "Project task id cannot be null");
    }

    public UUID projectId() { return projectId; }
    public UUID parentNodeId() { return parentNodeId; }
    public String code() { return code; }
    public String name() { return name; }
    public WbsNodeType type() { return type; }
    public UUID projectTaskId() { return projectTaskId; }
}
