package tech.kayys.numou.numoudomain;

import java.io.Serializable;

/** A named witness (syahid) to the akad. */
public record Witness(String name, WitnessSex sex) implements Serializable {
    public Witness {
        name = name == null ? "" : name.trim();
    }
}
