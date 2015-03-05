package com.coinomi.core.coins;


import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.utils.MonetaryFormat;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;


import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
abstract public class CoinType extends NetworkParameters implements Serializable{
    private static final long serialVersionUID = 1L;

    private static final String BIP_44_KEY_PATH = "44H/%dH/%dH";

    protected String name;
    protected String symbol;
    protected String uriScheme;
    protected Integer bip44Index;
    protected Integer unitExponent;
    protected Coin feePerKb;
    protected Coin minNonDust;
    protected Coin softDustLimit;
    protected SoftDustPolicy softDustPolicy;


    public String getName() {
        return checkNotNull(name, "A coin failed to set a name");
    }

    public String getSymbol() {
        return checkNotNull(symbol, "A coin failed to set a symbol");
    }

    public String getUriScheme() {
        return checkNotNull(uriScheme, "A coin failed to set a URI scheme");
    }

    public int getBip44Index() {
        return checkNotNull(bip44Index, "A coin failed to set a BIP 44 index");
    }

    public int getUnitExponent() {
        return checkNotNull(unitExponent, "A coin failed to set a unit exponent");
    }

    public Coin getFeePerKb() {
        return checkNotNull(feePerKb, "A coin failed to set a fee per kilobyte");
    }

    public Coin getMinNonDust() {
        return checkNotNull(minNonDust, "A coin failed to set a minimum amount to be considered not dust");
    }

    public Coin getSoftDustLimit() {
        return checkNotNull(softDustLimit, "A coin failed to set a soft dust limit");
    }

    public SoftDustPolicy getSoftDustPolicy() {
        return checkNotNull(softDustPolicy, "A coin failed to set a soft dust policy");
    }

    public List<ChildNumber> getBip44Path(int account) {
        String path = String.format(BIP_44_KEY_PATH, bip44Index, account);
        return HDUtils.parsePath(path);
    }

    /**
     * Returns a 1 coin of this type with the correct amount of units (satoshis)
     */
    public Coin getOneCoin() {
        BigInteger units = BigInteger.TEN.pow(getUnitExponent());
        return Coin.valueOf(units.longValue());
    }

    @Override
    public String getPaymentProtocolId() {
        throw new RuntimeException("Method not implemented");
    }

    @Override
    public String toString() {
        return "Coin{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", bip44Index=" + bip44Index +
                '}';
    }

    public MonetaryFormat getMonetaryFormat() {
        MonetaryFormat monetaryFormat = new MonetaryFormat()
                .shift(0).minDecimals(2).noCode().code(0, symbol).postfixCode();
        switch (unitExponent) {
            case 8:
                return monetaryFormat.optionalDecimals(2, 2, 2);
            case 6:
                return monetaryFormat.optionalDecimals(2, 2);
            case 4:
                return monetaryFormat.optionalDecimals(2);
            default:
                return monetaryFormat.minDecimals(unitExponent);
        }
    }
}
