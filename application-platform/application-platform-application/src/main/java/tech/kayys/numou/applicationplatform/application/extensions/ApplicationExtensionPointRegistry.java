package tech.kayys.numou.applicationplatform.application.extensions;

import java.util.List;
import java.util.Optional;

/** Host composition registry for extension contracts; it does not execute package code. */
public interface ApplicationExtensionPointRegistry {
    void register(ApplicationExtensionPoint extensionPoint);
    boolean unregister(String id, String expectedContractVersion);
    Optional<ApplicationExtensionPoint> find(String id);
    List<ApplicationExtensionPoint> all();
}
