
package tech.kayys.syirkah.accounting.domain.consolidation;

import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GroupHierarchy {

    private final String hierarchyId;
    private final String groupName;
    private final GroupMember parent;
    private final List<GroupMember> subsidiaries = new ArrayList<>();

    public GroupHierarchy(String hierarchyId, String groupName, GroupMember parent) {
        this.hierarchyId = Objects.requireNonNull(hierarchyId);
        this.groupName = Objects.requireNonNull(groupName);
        this.parent = Objects.requireNonNull(parent);
    }

    public void addSubsidiary(GroupMember subsidiary) {
        subsidiaries.add(Objects.requireNonNull(subsidiary));
    }

    public String hierarchyId() { return hierarchyId; }
    public String groupName() { return groupName; }
    public GroupMember parent() { return parent; }
    public List<GroupMember> subsidiaries() { return Collections.unmodifiableList(subsidiaries); }
}
