package com.coinomi.core.messages;

import com.coinomi.core.wallet.AbstractTransaction;

import java.io.Serializable;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public interface MessageFactory {
    int maxMessageSize();

    boolean canHandlePublicMessages();

    boolean canHandlePrivateMessages();

    TxMessage createPublicMessage(String message);

    // TODO change to abstract transaction
    @Nullable
    TxMessage extractPublicMessage(AbstractTransaction transaction);
}
