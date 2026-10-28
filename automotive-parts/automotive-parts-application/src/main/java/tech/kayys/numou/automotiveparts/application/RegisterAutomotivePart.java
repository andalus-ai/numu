package tech.kayys.numou.automotiveparts.application;

import tech.kayys.numou.automotiveparts.domain.AutomotivePartProfile;
import tech.kayys.numou.automotiveparts.spi.AutomotivePartRepository;

import java.util.Objects;

/** Registers an automotive profile; uniqueness is enforced atomically by the repository adapter. */
public final class RegisterAutomotivePart {
    private final AutomotivePartRepository repository;

    public RegisterAutomotivePart(AutomotivePartRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
    }

    public void execute(AutomotivePartProfile profile) {
        Objects.requireNonNull(profile, "profile is required");
        repository.saveProfile(profile);
    }
}