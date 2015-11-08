package com.coinomi.core.coins.nxt;

import java.util.List;

public interface Transaction extends Comparable<Transaction> {

    public static interface Builder {

        Builder recipientId(long recipientId);

        Builder referencedTransactionFullHash(String referencedTransactionFullHash);

        Builder message(Appendix.Message message);

        Builder encryptedMessage(Appendix.EncryptedMessage encryptedMessage);

        Builder encryptToSelfMessage(Appendix.EncryptToSelfMessage encryptToSelfMessage);

        Builder publicKeyAnnouncement(Appendix.PublicKeyAnnouncement publicKeyAnnouncement);

        Transaction build() throws NxtException.NotValidException;

    }

    long getId();

    String getStringId();

    long getSenderId();

    byte[] getSenderPublicKey();

    long getRecipientId();

    int getHeight();

    int getTimestamp();

    int getConfirmations();

    int getBlockTimestamp();

    short getDeadline();

    int getExpiration();

    long getAmountNQT();

    long getFeeNQT();

    String getReferencedTransactionFullHash();

    byte[] getSignature();

    String getFullHash();

    TransactionType getType();

    Attachment getAttachment();

    void sign(String secretPhrase);

    byte[] getBytes();

    byte[] getUnsignedBytes();

    //JSONObject getJSONObject();

    byte getVersion();

    Appendix.Message getMessage();

    Appendix.EncryptedMessage getEncryptedMessage();

    Appendix.EncryptToSelfMessage getEncryptToSelfMessage();

    List<? extends Appendix> getAppendages();

    /*
    Collection<TransactionType> getPhasingTransactionTypes();

    Collection<TransactionType> getPhasedTransactionTypes();
    */

    int getECBlockHeight();

    long getECBlockId();

}
