package com.coinomi.core.wallet.families.clams;

import com.coinomi.core.messages.MessageFactory;
import com.coinomi.core.messages.TxMessage;
import com.google.common.base.Charsets;

import org.bitcoinj.core.Transaction;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
public class ClamsTxMessage implements TxMessage {
    public static final int MAX_MESSAGE_BYTES = 140;

    private String message;

    ClamsTxMessage(String message) {
        setMessage(message);
    }

    private transient static ClamsMessageFactory instance = new ClamsMessageFactory();
    public static MessageFactory getFactory() {
        return instance;
    }

    public static ClamsTxMessage create(String message) throws IllegalArgumentException {
        return new ClamsTxMessage(message);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        checkArgument(serialize(message).length <= MAX_MESSAGE_BYTES, "Message is too big");
        this.message = message;
    }

    @Nullable
    public static ClamsTxMessage parse(Transaction tx) {
        byte[] bytes = tx.getExtraBytes();
        if (bytes == null || bytes.length == 0) return null;
        checkArgument(bytes.length <= MAX_MESSAGE_BYTES, "Maximum data size exceeded");

        return new ClamsTxMessage(new String(bytes, Charsets.UTF_8));
    }

    public boolean isEmpty() {
        return isNullOrEmpty(message);
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    @Override
    public Type getType() {
        return Type.PUBLIC; // Only public is supported
    }

    @Override
    public String toString() {
        return message;
    }

    @Override
    public void serializeTo(Transaction transaction) {
        transaction.setExtraBytes(serialize(message));
    }

    static byte[] serialize(String message) {
        return message.getBytes(Charsets.UTF_8);
    }

    public static class ClamsMessageFactory implements MessageFactory {
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
            return false;
        }

        @Override
        public TxMessage createPublicMessage(String message) {
            return create(message);
        }

        @Override
        @Nullable
        public TxMessage extractPublicMessage(Transaction transaction) {
            return parse(transaction);
        }
    }
}