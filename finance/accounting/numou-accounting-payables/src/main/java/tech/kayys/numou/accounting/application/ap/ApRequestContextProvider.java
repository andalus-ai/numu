package tech.kayys.numou.accounting.application.ap;

/** Resolves tenant and actor from a trusted, verified runtime; never from request payload fields. */
@FunctionalInterface
public interface ApRequestContextProvider {
    ApRequestContext requireContext();
}
