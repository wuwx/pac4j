package org.pac4j.core.exception;

/**
 * Exception for bad credentials.
 *
 * @author Jerome Leleu
 * @since 1.8.0
 */
public class BadCredentialsException extends CredentialsException {

    private static final long serialVersionUID = 106849753775292065L;

    /**
     * <p>Constructor for BadCredentialsException.</p>
     *
     * @param message a {@link java.lang.String} object
     */
    public BadCredentialsException(final String message) {
        super(message);
    }

    /**
     * <p>Constructor for BadCredentialsException.</p>
     *
     * @param t a {@link java.lang.Throwable} object
     */
    public BadCredentialsException(final Throwable t) {
        super(t);
    }

    /**
     * <p>Constructor for BadCredentialsException.</p>
     *
     * @param message a {@link java.lang.String} object
     * @param t a {@link java.lang.Throwable} object
     */
    public BadCredentialsException(String message, Throwable t) {
        super(message, t);
    }
}
