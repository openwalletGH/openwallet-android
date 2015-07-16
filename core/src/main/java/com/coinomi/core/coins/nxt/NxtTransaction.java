package com.coinomi.core.coins.nxt;

import org.spongycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by vbcs on 14/7/2015.
 * Simple implementation of Nxt Transaction - supports only simple payments
 */

public class NxtTransaction {
    byte type = 0; // TYPE_PAYMENT
    byte version = 1;
    byte subType = 0; //SUBTYPE_PAYMENT_ORDINARY_PAYMENT
    int timestamp;
    short deadline = 1440; //default deadline
    byte[] senderPublicKey;
    Long recipientId;
    Long amountNQT; // 1nxt = 10^8 NQT
    Long feeNQT;
    byte[] referencedTransactionFullHash = new byte[32];
    byte[] signature = new byte[64];
    int ecBlockHeight;
    Long ecBlockId;

    byte[] signedTxBytes;


    NxtTransaction(byte[] senderPublicKey, Long recipientId, Long amountNQT)
    {
        this.senderPublicKey = senderPublicKey;
        this.recipientId = recipientId;
        this.amountNQT = amountNQT;
        this.feeNQT = 100000000L;
        this.timestamp = Convert.toNxtEpochTime(System.currentTimeMillis());
        getEcBlock(timestamp);

    }

    NxtTransaction(byte[] senderPublicKey, String recipientRS, Long amountNQT)
    {
        this.senderPublicKey = senderPublicKey;
        this.recipientId = Convert.parseAccountId(recipientRS);
        this.amountNQT = amountNQT;
        this.feeNQT = 100000000L;
        this.timestamp = Convert.toNxtEpochTime(System.currentTimeMillis());
        getEcBlock(timestamp);

    }

    void setDeadline(short deadline)
    {
        this.deadline = deadline;
    }

    void setReferencedTransactionFullHash(byte[] referencedTransactionFullHash)
    {
        this.referencedTransactionFullHash = referencedTransactionFullHash;
    }

    void setReferencedTransactionFullHash(String referencedTransactionFullHash)
    {
        this.referencedTransactionFullHash = Hex.decode(referencedTransactionFullHash);
    }

    void getEcBlock(int timestamp)
    {
        this.ecBlockHeight = 0;
        this.ecBlockId = 0L;
        //fetch from nxt server -> f.e. ?requestType=getECBlock&timestamp=29152431
        // response:
        //{
        //    "ecBlockHeight": 120489,
        //        "requestProcessingTime": 19,
        //        "ecBlockId": "14439979832634697399",
        //        "timestamp": 29152431
        //}

    }

    byte[] getBytes()
    {
        ByteBuffer buffer = ByteBuffer.allocate(getSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(type);
        buffer.put((byte) ( (version << 4 ) | subType ) );
        buffer.putInt(timestamp);
        buffer.putShort(deadline);
        buffer.put(senderPublicKey);
        buffer.putLong(recipientId);
        buffer.putLong(amountNQT);
        buffer.putLong(feeNQT);
        buffer.put(referencedTransactionFullHash);
        buffer.put(signature);
        buffer.putInt(getFlags());
        buffer.putInt(ecBlockHeight);
        buffer.putLong(ecBlockId);

        return buffer.array();
    }

    byte[] getSignedTxBytes(String secretPhrase)
    {
        if (signedTxBytes == null)
        {
            signature = Crypto.sign(getBytes(), secretPhrase);
            signedTxBytes = getBytes();
        }
        return signedTxBytes;
    }



    private int getSize()
    {
        return signatureOffset() + 64 + 4 + 4 + 8;
    }

    private int signatureOffset()
    {
        return 1 + 1 + 4 + 2 + 32 + 8 + 8 + 8 + 32;
    }

    private int getFlags()
    {
        return 0;
    }

}
