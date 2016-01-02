package com.coinomi.core.wallet.families.vpncoin;

import com.coinomi.core.messages.MessageFactory;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.AbstractTransaction;
import com.coinomi.core.wallet.families.bitcoin.BitTransaction;
import com.google.common.base.Charsets;

import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
public class VpncoinTxMessage implements TxMessage {
    private static final Logger log = LoggerFactory.getLogger(VpncoinTxMessage.class);

    static final int SIZE_LENGTH = 4;

    public static final int MAX_TX_DATA      = 65536;
    public static final int MAX_TX_DATA_FROM = 128;
    public static final int MAX_TX_DATA_SUBJ = 128;
    public static final int MAX_TX_DATA_MSG  = MAX_TX_DATA - MAX_TX_DATA_FROM - MAX_TX_DATA_SUBJ;

    static final Pattern MESSAGE_REGEX =
            Pattern.compile("(?s)@(?:FROM|SUBJ|MSG)=.*?(?=@(?:FROM|SUBJ|MSG)=|$)");

    static final String FROM = "@FROM=";
    static final String SUBJ = "@SUBJ=";
    static final String MSG  = "@MSG=";

    private String from;
    private String subject;
    private String message;

    VpncoinTxMessage() { }

    VpncoinTxMessage(String message) {
        setMessage(message);
    }

    VpncoinTxMessage(String from, String subject, String message) {
        setFrom(from);
        setSubject(subject);
        setMessage(message);
    }

    private transient static VpncoinMessageFactory instance = new VpncoinMessageFactory();
    public static MessageFactory getFactory() {
        return instance;
    }

    public static VpncoinTxMessage create(String message) throws IllegalArgumentException {
        return new VpncoinTxMessage(message);
    }

    public String getFrom() {
        return from;
    }

    public String getSubject() {
        return subject;
    }

    public String getMessage() {
        return message;
    }

    public void setFrom(String from) {
        checkArgument(from.length() <= MAX_TX_DATA_FROM, "'From' field size exceeded");
        this.from = from;
    }

    public void setSubject(String subject) {
        checkArgument(subject.length() <= MAX_TX_DATA_SUBJ, "'Subject' field size exceeded");
        this.subject = subject;
    }

    public void setMessage(String message) {
        checkArgument(message.length() <= MAX_TX_DATA_MSG, "'Message' field size exceeded");
        this.message = message;
    }

    @Nullable
    public static VpncoinTxMessage parse(AbstractTransaction tx) {
        Transaction rawTx = null;
        byte[] bytes;
        String fullMessage = null;
        try {
            rawTx = ((BitTransaction) tx).getRawTransaction();
            bytes = rawTx.getExtraBytes();
            if (bytes == null || bytes.length == 0) return null;

            fullMessage = new String(bytes, Charsets.UTF_8);
            return parseUnencrypted(fullMessage);
        } catch (Exception e) {
            if (rawTx == null || fullMessage == null) return null;
            try {
                return parseEncrypted(rawTx.getTime(), fullMessage);
            } catch (Exception e1) {
                log.info("Could not parse message: {}", e1.getMessage());
                return null;
            }
        }
    }

    @Nullable
    public static VpncoinTxMessage parse(String fullMessage) {
        if (fullMessage == null || fullMessage.length() == 0) {
            return null;
        }

        try {
            return parseUnencrypted(fullMessage);
        } catch (Exception e) {
            log.info("Could not parse message: {}", e.getLocalizedMessage());
            return null;
        }
    }

    public boolean isEmpty() {
        return isNullOrEmpty(from) && isNullOrEmpty(subject) && isNullOrEmpty(message);
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
        StringBuilder sb = new StringBuilder();
        if (!isNullOrEmpty(from)) {
            sb.append(from);
        }
        if (!isNullOrEmpty(subject)) {
            if (sb.length() != 0) sb.append("\n\n");
            sb.append(subject);
        }
        if (!isNullOrEmpty(message)) {
            if (sb.length() != 0) sb.append("\n\n");
            sb.append(message);
        }

        return sb.toString();
    }

    @Override
    public void serializeTo(AbstractTransaction transaction) {
        if (transaction instanceof BitTransaction) {
            Transaction rawTx = ((BitTransaction) transaction).getRawTransaction();
            rawTx.setExtraBytes(serialize());
        }
    }

    byte[] serialize() {
        StringBuilder sb = new StringBuilder();
        if (!isNullOrEmpty(from)) {
            sb.append(FROM);
            sb.append(from);
        }
        if (!isNullOrEmpty(subject)) {
            sb.append(SUBJ);
            sb.append(subject);
        }
        if (!isNullOrEmpty(message)) {
            sb.append(MSG);
            sb.append(message);
        }

        return sb.toString().getBytes(Charsets.UTF_8);
    }

    static VpncoinTxMessage parseEncrypted(long key, String fullMessage) throws Exception {
        return parseUnencrypted(decrypt(key, fullMessage));
    }

    static VpncoinTxMessage parseUnencrypted(String fullMessage) throws Exception {
        checkArgument(fullMessage.length() <= MAX_TX_DATA, "Maximum data size exceeded");

        Matcher matcher = MESSAGE_REGEX.matcher(fullMessage);
        VpncoinTxMessage msg = new VpncoinTxMessage();
        while (matcher.find()) {
            String part = matcher.group();
            // Be more relaxed about each field size that we receive from the network
            if (part.startsWith(FROM)) {
                msg.from = part.replace(FROM, "");
            } else if (part.startsWith(SUBJ)) {
                msg.subject = part.replace(SUBJ, "");
            } else if (part.startsWith(MSG)) {
                msg.message = part.replace(MSG, "");
            }
        }
        if (msg.isEmpty()) {
            throw new Exception("Message is empty");
        }
        return msg;
    }

    static int checksum(byte[] data, int offset) {
        int crc = 0xffff;
        for (int i = offset; i < data.length; i++) {
            byte b = data[i];
            crc = ((crc >> 4) & 0x0fff) ^ CRC_TBL[((crc ^ b) & 15)];
            b >>= 4;
            crc = ((crc >> 4) & 0x0fff) ^ CRC_TBL[((crc ^ b) & 15)];
        }
        return ~crc & 0xffff;
    }

    static byte[] uncompress(ByteBuffer compressed) throws DataFormatException {
        if (compressed.remaining() <= SIZE_LENGTH) {
            throw new DataFormatException("Invalid compressed data size");
        }
        Inflater inflater = new Inflater();

        // The first 4 bytes of the input is an integer encoding the size of the uncompressed data
        int expectedSize = compressed.getInt();

        // Check the maximum data that we are willing to handle
        if (expectedSize > MAX_TX_DATA * 2) {
            throw new DataFormatException("Maximum data size exceeded");
        }

        // Ignore the size bytes when uncompressing
        inflater.setInput(compressed.array(), compressed.position(), compressed.remaining());
        byte[] buffer = new byte[expectedSize];
        int size = inflater.inflate(buffer);

        // Check that the uncompressed size matches the expected size
        if (expectedSize != size) throw new DataFormatException("Unexpected data size");
        if (!inflater.finished()) throw new DataFormatException("Data larger than expected");

        return buffer;
    }

    static String decrypt(long key, String fullMessage) throws DataFormatException {
        byte[] bytes = decryptBytes(key, Base64.decode(fullMessage));
        return new String(bytes);
    }

    static byte[] decryptBytes(long key, byte[] data) throws DataFormatException {
        byte[] m_keyParts = makeKey(key);
        byte version = data[0];

        if (version != 3) {  //we only work with version 3
            throw new IllegalArgumentException("Invalid version or not a cyphertext.");
        }

        byte flags = data[1];
        ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(data, 2, data.length));

        byte lastByte = 0;
        while (buffer.hasRemaining()) {
            int pos = buffer.position();
            byte curByte = buffer.get();
            buffer.position(pos);
            buffer.put((byte)(curByte ^ lastByte ^ m_keyParts[pos % 8]));
            lastByte = curByte;
        }
        buffer.rewind();
        //chop off the random number at the start
        buffer.get();

        boolean integrityOk = true;
        if (CryptoFlag.CryptoFlagChecksum.isSet(flags)) {
            int storedChecksum = buffer.getShort() & 0xffff;
            int checksum = checksum(buffer.array(), buffer.position());
            integrityOk = (checksum == storedChecksum);
        } else if (CryptoFlag.CryptoFlagHash.isSet(flags)) {
            throw new RuntimeException("Not implemented");
        }

        if (!integrityOk) throw new DataFormatException("Integrity failed");

        if (CryptoFlag.CryptoFlagCompression.isSet(flags)) {
            return uncompress(buffer);
        } else {
            return Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.array().length);
        }
    }

    private static byte[] makeKey(long key) {
        byte[] m_keyParts = new byte[8];
        for (int i=0;i<8;i++) {
            long part = key;
            for (int j=i; j>0; j--)
                part = part >> 8;
            part = part & 0xff;
            m_keyParts[i] = (byte) part;
        }
        return m_keyParts;
    }

    enum CryptoFlag {
        CryptoFlagNone((byte)0),
        CryptoFlagCompression((byte)0x01),
        CryptoFlagChecksum((byte)0x02),
        CryptoFlagHash((byte)0x04);

        private final byte flag;

        CryptoFlag(byte b) {
            flag = b;
        }

        public boolean isSet(byte flags) {
            return (flags & flag) == flag;
        }
    }

    static final int CRC_TBL[] = {
            0x0000, 0x1081, 0x2102, 0x3183,
            0x4204, 0x5285, 0x6306, 0x7387,
            0x8408, 0x9489, 0xa50a, 0xb58b,
            0xc60c, 0xd68d, 0xe70e, 0xf78f
    };

    public static class VpncoinMessageFactory implements MessageFactory {
        @Override
        public int maxMessageSizeBytes() {
            return MAX_TX_DATA_MSG;
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
        public TxMessage extractPublicMessage(AbstractTransaction transaction) {
            return parse(transaction);
        }
    }
}