package io.surveyhub.security;

/** The credentials are missing or invalid. The message is safe to log but is not shown to clients in detail. */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
}
