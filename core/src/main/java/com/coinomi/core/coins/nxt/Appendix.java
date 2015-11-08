package com.coinomi.core.coins.nxt;

//import org.json.simple.JSONObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public interface Appendix {

    int getSize();
    void putBytes(ByteBuffer buffer);
    //JSONObject getJSONObject();
    byte getVersion();

    static abstract class AbstractAppendix implements Appendix {

        private final byte version;

        AbstractAppendix(JSONObject attachmentData) throws JSONException {
            Long l = attachmentData.getLong("version." + getAppendixName());
            version = (byte) (l == null ? 0 : l);
        }

        AbstractAppendix(ByteBuffer buffer, byte transactionVersion) {
            if (transactionVersion == 0) {
                version = 0;
            } else {
                version = buffer.get();
            }
        }

        AbstractAppendix(int version) {
            this.version = (byte) version;
        }

        AbstractAppendix() {
            this.version = (byte)(1);
        }

        abstract String getAppendixName();

        @Override
        public final int getSize() {
            return getMySize() + (version > 0 ? 1 : 0);
        }

        abstract int getMySize();

        @Override
        public final void putBytes(ByteBuffer buffer) {
            if (version > 0) {
                buffer.put(version);
            }
            putMyBytes(buffer);
        }

        abstract void putMyBytes(ByteBuffer buffer);

        /*@Override
        public final JSONObject getJSONObject() {
            JSONObject json = new JSONObject();
            if (version > 0) {
                json.put("version." + getAppendixName(), version);
            }
            putMyJSON(json);
            return json;
        }

        abstract void putMyJSON(JSONObject json);
        */
        @Override
        public final byte getVersion() {
            return version;
        }

        boolean verifyVersion(byte transactionVersion) {
            return transactionVersion == 0 ? version == 0 : version > 0;
        }

    }

    public static class Message extends AbstractAppendix {

        static Message parse(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
            if (!attachmentData.has("message")) {
                return null;
            }
            return new Message(attachmentData);
        }


        private final byte[] message;
        private final boolean isText;

        Message(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            int messageLength = buffer.getInt();
            this.isText = messageLength < 0; // ugly hack
            if (messageLength < 0) {
                messageLength &= Integer.MAX_VALUE;
            }
            if (messageLength > Constants.MAX_ARBITRARY_MESSAGE_LENGTH) {
                throw new NxtException.NotValidException("Invalid arbitrary message length: " + messageLength);
            }
            this.message = new byte[messageLength];
            buffer.get(this.message);
        }

        Message(JSONObject attachmentData) throws JSONException {
            super(attachmentData);
            String messageString = (String)attachmentData.get("message");
            this.isText = Boolean.TRUE.equals(attachmentData.get("messageIsText"));
            this.message = isText ? Convert.toBytes(messageString) : Convert.parseHexString(messageString);
        }


        public Message(byte[] message) {
            this.message = message;
            this.isText = false;
        }

        public Message(String string) {
            this.message = Convert.toBytes(string);
            this.isText = true;
        }

        @Override
        String getAppendixName() {
            return "Message";
        }

        @Override
        int getMySize() {
            return 4 + message.length;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(isText ? (message.length | Integer.MIN_VALUE) : message.length);
            buffer.put(message);
        }

        /*@Override
        void putMyJSON(JSONObject json) {
            json.put("message", isText ? Convert.toString(message) : Convert.toHexString(message));
            json.put("messageIsText", isText);
        }
        */
        public byte[] getMessage() {
            return message;
        }

        public boolean isText() {
            return isText;
        }
    }

    abstract static class AbstractEncryptedMessage extends AbstractAppendix {

        private final EncryptedData encryptedData;
        private final boolean isText;

        private AbstractEncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws NxtException.NotValidException {
            super(buffer, transactionVersion);
            int length = buffer.getInt();
            this.isText = length < 0;
            if (length < 0) {
                length &= Integer.MAX_VALUE;
            }
            this.encryptedData = EncryptedData.readEncryptedData(buffer, length, Constants.MAX_ENCRYPTED_MESSAGE_LENGTH);
        }

        private AbstractEncryptedMessage(JSONObject attachmentJSON, JSONObject encryptedMessageJSON) throws JSONException {
            super(attachmentJSON);
            byte[] data = Convert.parseHexString((String)encryptedMessageJSON.get("data"));
            byte[] nonce = Convert.parseHexString((String)encryptedMessageJSON.get("nonce"));
            this.encryptedData = new EncryptedData(data, nonce);
            this.isText = Boolean.TRUE.equals(encryptedMessageJSON.get("isText"));
        }

        private AbstractEncryptedMessage(EncryptedData encryptedData, boolean isText) {
            this.encryptedData = encryptedData;
            this.isText = isText;
        }

        @Override
        int getMySize() {
            return 4 + encryptedData.getSize();
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.putInt(isText ? (encryptedData.getData().length | Integer.MIN_VALUE) : encryptedData.getData().length);
            buffer.put(encryptedData.getData());
            buffer.put(encryptedData.getNonce());
        }

        /*@Override
        void putMyJSON(JSONObject json) {
            json.put("data", Convert.toHexString(encryptedData.getData()));
            json.put("nonce", Convert.toHexString(encryptedData.getNonce()));
            json.put("isText", isText);
        }
        */

        public final EncryptedData getEncryptedData() {
            return encryptedData;
        }

        public final boolean isText() {
            return isText;
        }

    }

    public static class EncryptedMessage extends AbstractEncryptedMessage {

        static EncryptedMessage parse(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
            if (!attachmentData.has("encryptedMessage") ) {
                return null;
            }
            return new EncryptedMessage(attachmentData);
        }


        EncryptedMessage(ByteBuffer buffer, byte transactionVersion) throws NxtException.ValidationException {
            super(buffer, transactionVersion);
        }

        EncryptedMessage(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
            super(attachmentData, (JSONObject)attachmentData.get("encryptedMessage"));
        }

        public EncryptedMessage(EncryptedData encryptedData, boolean isText) {
            super(encryptedData, isText);
        }


        @Override
        String getAppendixName() {
            return "EncryptedMessage";
        }
        /*
        @Override
        void putMyJSON(JSONObject json) {
            JSONObject encryptedMessageJSON = new JSONObject();
            super.putMyJSON(encryptedMessageJSON);
            json.put("encryptedMessage", encryptedMessageJSON);
        }
        */

    }

    public static class EncryptToSelfMessage extends AbstractEncryptedMessage {

        static EncryptToSelfMessage parse(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
            if (!attachmentData.has("encryptToSelfMessage") ) {
                return null;
            }
            return new EncryptToSelfMessage(attachmentData);
        }


        EncryptToSelfMessage(ByteBuffer buffer, byte transactionVersion) throws NxtException.ValidationException {
            super(buffer, transactionVersion);
        }

        EncryptToSelfMessage(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
            super(attachmentData, (JSONObject)attachmentData.get("encryptToSelfMessage"));
        }

        public EncryptToSelfMessage(EncryptedData encryptedData, boolean isText) {
            super(encryptedData, isText);
        }

        @Override
        String getAppendixName() {
            return "EncryptToSelfMessage";
        }

        /*@Override
        void putMyJSON(JSONObject json) {
            JSONObject encryptToSelfMessageJSON = new JSONObject();
            super.putMyJSON(encryptToSelfMessageJSON);
            json.put("encryptToSelfMessage", encryptToSelfMessageJSON);
        }
        */

    }

    public static class PublicKeyAnnouncement extends AbstractAppendix {

        static PublicKeyAnnouncement parse(JSONObject attachmentData) throws NxtException.NotValidException, JSONException {
            if (!attachmentData.has("recipientPublicKey")) {
                return null;
            }
            return new PublicKeyAnnouncement(attachmentData);
        }

        private final byte[] publicKey;

        PublicKeyAnnouncement(ByteBuffer buffer, byte transactionVersion) {
            super(buffer, transactionVersion);
            this.publicKey = new byte[32];
            buffer.get(this.publicKey);
        }

        PublicKeyAnnouncement(JSONObject attachmentData) throws JSONException {
            super(attachmentData);
            this.publicKey = Convert.parseHexString((String)attachmentData.get("recipientPublicKey"));
        }

        public PublicKeyAnnouncement(byte[] publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        String getAppendixName() {
            return "PublicKeyAnnouncement";
        }

        @Override
        int getMySize() {
            return 32;
        }

        @Override
        void putMyBytes(ByteBuffer buffer) {
            buffer.put(publicKey);
        }

        /*@Override
        void putMyJSON(JSONObject json) {
            json.put("recipientPublicKey", Convert.toHexString(publicKey));
        }*/


        public byte[] getPublicKey() {
            return publicKey;
        }

    }

}
