package com.coinomi.core.messages;

import com.coinomi.core.wallet.AbstractTransaction;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public interface MessageFactory {
    int maxMessageSizeBytes();

    boolean canHandlePublicMessages();

    boolean canHandlePrivateMessages();

    TxMessage createPublicMessage(String message);

    @Nullable
    TxMessage extractPublicMessage(AbstractTransaction transaction);
}
