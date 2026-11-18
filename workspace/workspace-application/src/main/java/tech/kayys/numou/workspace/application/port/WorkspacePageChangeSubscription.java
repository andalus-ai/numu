package tech.kayys.numou.workspace.application.port;

/**
 * A live page-change subscription. Hosts must invoke the revocation callback when access is
 * withdrawn, the page/tenant is removed, or the transport is shutting down.
 */
public interface WorkspacePageChangeSubscription extends AutoCloseable {
    /** Registers a callback invoked once when the host revokes this subscription. */
    void onRevoked(Runnable callback);

    /** Closes the subscription without representing an authorization revocation. */
    @Override
    void close();
}
