package tech.kayys.syirkah.asset.domain.relationship;

/** The semantic kind of relationship between two assets (see ASSET-16/17). */
public enum AssetRelationshipType {

    /** relatedAsset is the parent of this asset. */
    PARENT(true),

    /** relatedAsset is a child of this asset. */
    CHILD(true),

    /** relatedAsset is a component of this asset. */
    COMPONENT(true),

    /** source is a component of target (ASSET-17 hierarchical). */
    COMPONENT_OF(true),

    /** source is installed on target (ASSET-17 hierarchical). */
    INSTALLED_ON(true),

    /** source is attached to target (ASSET-17 hierarchical). */
    ATTACHED_TO(true),

    /** relatedAsset is a spare part for this asset. */
    SPARE(false),

    /** generic linkage. */
    LINKED(false),

    /** generic non-hierarchical association. */
    RELATED_TO(false);

    private final boolean hierarchical;

    AssetRelationshipType(boolean hierarchical) {
        this.hierarchical = hierarchical;
    }

    public boolean hierarchical() {
        return hierarchical;
    }
}
