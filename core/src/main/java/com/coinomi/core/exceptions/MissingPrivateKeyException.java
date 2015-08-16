package com.coinomi.core.exceptions;

/**
 * @author John L. Jegutanis
 */
public class MissingPrivateKeyException extends Exception {
    public MissingPrivateKeyException(String message) {
        super(message);
    }

    public MissingPrivateKeyException(Throwable cause) {
        super(cause);
    }
}
