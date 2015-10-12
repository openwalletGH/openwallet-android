package com.coinomi.core.exceptions;

/**
 * Created by vbcs on 29/9/2015.
 */
public class TransactionException extends Exception {
    public TransactionException(String message) {
        super(message);
    }

    public TransactionException(Throwable cause) {
        super(cause);
    }
}
