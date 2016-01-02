package com.coinomi.core.wallet.families.clams;

import com.coinomi.core.messages.MessageFactory;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.families.bitcoin.BitTransaction;
import com.google.common.base.Charsets;

import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
public class ClamsTxMessage implements TxMessage {
    private static final Logger log = LoggerFactory.getLogger(ClamsTxMessage.class);

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
    public static ClamsTxMessage parse(AbstractTransaction  tx) {
        try {
            Transaction rawTx = ((BitTransaction) tx).getRawTransaction();
            byte[] bytes = rawTx.getExtraBytes();
            if (bytes == null || bytes.length == 0) return null;
            checkArgument(bytes.length <= MAX_MESSAGE_BYTES, "Maximum data size exceeded");

            return new ClamsTxMessage(new String(bytes, Charsets.UTF_8));
        } catch (Exception e) {
            log.info("Could not parse message: {}", e.getMessage());
            return null;
        }
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
    public void serializeTo(AbstractTransaction transaction) {
        if (transaction instanceof BitTransaction) {
            Transaction rawTx = ((BitTransaction) transaction).getRawTransaction();
            rawTx.setExtraBytes(serialize(message));
        }
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

        @Nullable
        @Override
        public TxMessage extractPublicMessage(AbstractTransaction transaction) {
            return parse(transaction);
        }
    }
}