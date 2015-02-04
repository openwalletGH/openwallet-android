package com.coinomi.stratumj.messages;

/**
 * @author John L. Jegutanis
 */
public class MessageException extends Exception {
    public MessageException(String error, ResultMessage result) {
        super(error + ": " + result);
    }

    public MessageException(String errorMessage, String faultyRequest) {
        super(errorMessage + ": " + faultyRequest);
    }
}
