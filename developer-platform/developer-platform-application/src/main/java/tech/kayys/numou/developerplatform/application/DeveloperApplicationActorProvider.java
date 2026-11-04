package tech.kayys.numou.developerplatform.application;
/** Resolve tenant and actor from validated identity and verified membership, never request JSON. */
@FunctionalInterface public interface DeveloperApplicationActorProvider{DeveloperApplicationActor currentActor();}