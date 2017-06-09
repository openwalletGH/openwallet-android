package com.openwallet.core.wallet.families.nxt;

import com.openwallet.core.coins.nxt.Convert;
import com.openwallet.core.coins.nxt.Transaction;
import com.openwallet.core.messages.MessageFactory;
import com.openwallet.core.messages.TxMessage;
import com.openwallet.core.wallet.AbstractTransaction;

import javax.annotation.Nullable;


/**
 * @author vbcs
 */
public class NxtTxMessage implements TxMessage {
    public static final int MAX_MESSAGE_BYTES = 0;

    Type type;
    byte[] message;

    NxtTxMessage(Transaction transaction) {
        if (transaction.getMessage() != null && transaction.getMessage().isText() &&
                transaction.getMessage().getMessage().length > 0) {
            type = Type.PUBLIC;
            message = transaction.getMessage().getMessage();
        }

    }

    private transient static NxtMessageFactory instance = new NxtMessageFactory();
    public static MessageFactory getFactory() { return instance; }

    public String toString() {
        return Convert.toString(message);
    }

    @Override
    public void serializeTo(AbstractTransaction transaction) {

    }

    @Override
    public Type getType() {
        return type;
    }

    public static class NxtMessageFactory implements MessageFactory {
        @Override
        public int maxMessageSizeBytes() {
            return MAX_MESSAGE_BYTES;
        }

        @Override
        public boolean canHandlePublicMessages() {
            return true;
        }

        @Override
        public boolean canHandlePrivateMessages() {
            return true;
        }

        @Override
        public TxMessage createPublicMessage(String message) {
            return null;
        }

        @Nullable
        @Override
        public TxMessage extractPublicMessage(AbstractTransaction transaction) {
            return null;
        }
    }
}
