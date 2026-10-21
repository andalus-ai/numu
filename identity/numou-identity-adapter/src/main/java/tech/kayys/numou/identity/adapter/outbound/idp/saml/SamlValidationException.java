package tech.kayys.numou.identity.adapter.outbound.idp.saml;

/** Thrown when a SAML Response fails validation; mapped to a 400 by the resource. */
public class SamlValidationException extends Exception {

    public SamlValidationException(String message) {
        super(message);
    }

    public SamlValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
