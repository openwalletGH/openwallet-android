package com.coinomi.core.messages;

import org.bitcoinj.core.Transaction;

import java.io.Serializable;

/**
 * @author John L. Jegutanis
 */
public interface TxMessage extends Serializable {
    // TODO use an abstract transaction
    void serializeTo(Transaction transaction);

    enum Type {
        PUBLIC, PRIVATE
    }

    Type getType();
    String toString();
}
