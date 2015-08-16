package com.coinomi.core.exceptions;

/**
 * @author John L. Jegutanis
 */
public class AddressMalformedException extends Exception {
    public AddressMalformedException(String message) {
        super(message);
    }

    public AddressMalformedException(Throwable cause) {
        super(cause);
    }
}
