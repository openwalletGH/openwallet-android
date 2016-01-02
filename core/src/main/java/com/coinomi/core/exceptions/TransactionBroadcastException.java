package com.coinomi.core.exceptions;

/**
 * Created by vbcs on 29/9/2015.
 */
public class TransactionBroadcastException extends Exception {
    public TransactionBroadcastException(String message) {
        super(message);
    }

    public TransactionBroadcastException(Throwable cause) {
        super(cause);
    }
}
