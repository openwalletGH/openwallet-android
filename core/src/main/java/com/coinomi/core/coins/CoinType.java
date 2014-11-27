package com.coinomi.core.coins;


import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDUtils;

import java.io.Serializable;
import java.util.List;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 */
abstract public class CoinType extends NetworkParameters implements Serializable{
    private static final long serialVersionUID = 1L;

    private static final String BIP_44_KEY_PATH = "44H/%dH/%dH";

    protected String name;
    protected String symbol;
    protected String uriScheme;
    protected Integer bip44Index;
    protected Coin feePerKb;
    protected Coin minNonDust;
    protected Integer unitExponent;

    public String getName() {
        return checkNotNull(name);
    }

    public String getSymbol() {
        return checkNotNull(symbol);
    }

    public String getUriScheme() {
        return checkNotNull(uriScheme);
    }

    public int getBip44Index() {
        return checkNotNull(bip44Index);
    }

    public Coin getFeePerKb() {
        return checkNotNull(feePerKb);
    }

    public Coin getMinNonDust() {
        return checkNotNull(minNonDust);
    }

    public int getUnitExponent() {
        return checkNotNull(unitExponent);
    }

    public List<ChildNumber> getBip44Path(int account) {
        String path = String.format(BIP_44_KEY_PATH, bip44Index, account);
        return HDUtils.parsePath(path);
    }

    @Override
    public String getPaymentProtocolId() {
        throw new RuntimeException("Method not implemented");
    }

    @Nullable
    public static CoinType fromID(String id) {
        return CoinID.fromId(id).getCoinType();
    }

    @Override
    public String toString() {
        return "Coin{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", bip44Index=" + bip44Index +
                '}';
    }
}
