package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.coins.CoinType;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.spongycastle.util.encoders.Hex;

/**
 * @author John L. Jegutanis
 */
public class EmptyTransactionOutput extends TransactionOutput {
    private static EmptyTransactionOutput instance =
            new EmptyTransactionOutput(new FakeNetworkParameters(), null, Coin.ZERO,
                    Hex.decode("76a914000000000000000000000000000000000000000088ac"));

    static class FakeNetworkParameters extends NetworkParameters {
        @Override
        public String getPaymentProtocolId() {
            return "";
        }
    }

    private EmptyTransactionOutput(NetworkParameters params, Transaction parent, Coin value, byte[] scriptBytes) {
        super(params, parent, value, scriptBytes);
    }

    public static synchronized EmptyTransactionOutput get() {
        return instance;
    }

    @Override
    public int getIndex() {
        throw new IllegalArgumentException("Empty outputs don't have indexes");
    }
}
