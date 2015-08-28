package com.coinomi.core.coins;


import com.coinomi.core.messages.MessageFactory;
import com.coinomi.core.util.MonetaryFormat;
import com.google.common.base.Charsets;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDUtils;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;


import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
abstract public class CoinType extends NetworkParameters implements ValueType, Serializable {
    private static final long serialVersionUID = 1L;

    private static final String BIP_44_KEY_PATH = "44H/%dH/%dH";

    protected String name;
    protected String symbol;
    protected String uriScheme;
    protected Integer bip44Index;
    protected Integer unitExponent;
    protected Value feePerKb;
    protected Value minNonDust;
    protected Value softDustLimit;
    protected SoftDustPolicy softDustPolicy;
    protected FeePolicy feePolicy = FeePolicy.FEE_PER_KB;
    protected byte[] signedMessageHeader;

    private transient MonetaryFormat friendlyFormat;
    private transient MonetaryFormat plainFormat;
    private transient Value oneCoin;

    @Override
    public String getName() {
        return checkNotNull(name, "A coin failed to set a name");
    }

    public boolean isTestnet() {
        return id.endsWith("test");
    }

    @Override
    public String getSymbol() {
        return checkNotNull(symbol, "A coin failed to set a symbol");
    }

    public String getUriScheme() {
        return checkNotNull(uriScheme, "A coin failed to set a URI scheme");
    }

    public int getBip44Index() {
        return checkNotNull(bip44Index, "A coin failed to set a BIP 44 index");
    }

    @Override
    public int getUnitExponent() {
        return checkNotNull(unitExponent, "A coin failed to set a unit exponent");
    }

    @Deprecated
    public Coin getFeePerKb() {
        return feePerKb().toCoin();
    }

    public Value feePerKb() {
        return checkNotNull(feePerKb, "A coin failed to set a fee per kilobyte");
    }

    @Deprecated
    public Coin getMinNonDust() {
        return minNonDust().toCoin();
    }

    @Override
    public Value minNonDust() {
        return checkNotNull(minNonDust, "A coin failed to set a minimum amount to be considered not dust");
    }

    @Deprecated
    public Coin getSoftDustLimit() {
        return softDustLimit().toCoin();
    }

    public Value softDustLimit() {
        return checkNotNull(softDustLimit, "A coin failed to set a soft dust limit");
    }

    public SoftDustPolicy getSoftDustPolicy() {
        return checkNotNull(softDustPolicy, "A coin failed to set a soft dust policy");
    }

    public FeePolicy getFeePolicy() {
        return checkNotNull(feePolicy, "A coin failed to set a fee policy");
    }

    public byte[] getSignedMessageHeader() {
        return checkNotNull(signedMessageHeader, "A coin failed to set signed message header bytes");
    }

    public boolean canSignVerifyMessages() {
        return signedMessageHeader != null;
    }

    public boolean canHandleMessages() {
        return getMessagesFactory() != null;
    }

    @Nullable
    public MessageFactory getMessagesFactory() {
        return null;
    }

    protected static byte[] toBytes(String str) {
        return str.getBytes(Charsets.UTF_8);
    }

    public List<ChildNumber> getBip44Path(int account) {
        String path = String.format(BIP_44_KEY_PATH, bip44Index, account);
        return HDUtils.parsePath(path);
    }

    public Address address(String addressStr) throws AddressFormatException {
        return new Address(this, addressStr);
    }

    /**
     * Returns a 1 coin of this type with the correct amount of units (satoshis)
     * Use {@link com.coinomi.core.coins.CoinType:oneCoin}
     */
    @Deprecated
    public Coin getOneCoin() {
        BigInteger units = BigInteger.TEN.pow(getUnitExponent());
        return Coin.valueOf(units.longValue());
    }

    @Override
    public Value oneCoin() {
        if (oneCoin == null) {
            BigInteger units = BigInteger.TEN.pow(getUnitExponent());
            oneCoin = Value.valueOf(this, units.longValue());
        }
        return oneCoin;
    }

    @Override
    public Value value(String string) {
        return Value.parse(this, string);
    }

    @Override
    public Value value(Coin coin) {
        return Value.valueOf(this, coin);
    }

    @Override
    public Value value(long units) {
        return Value.valueOf(this, units);
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

    @Override
    public MonetaryFormat getMonetaryFormat() {
        if (friendlyFormat == null) {
            friendlyFormat = new MonetaryFormat()
                    .shift(0).minDecimals(2).code(0, symbol).postfixCode();
            switch (unitExponent) {
                case 8:
                    friendlyFormat = friendlyFormat.optionalDecimals(2, 2, 2);
                    break;
                case 6:
                    friendlyFormat = friendlyFormat.optionalDecimals(2, 2);
                    break;
                case 4:
                    friendlyFormat = friendlyFormat.optionalDecimals(2);
                    break;
                default:
                    friendlyFormat = friendlyFormat.minDecimals(unitExponent);
            }
        }
        return friendlyFormat;
    }

    @Override
    public MonetaryFormat getPlainFormat() {
        if (plainFormat == null) {
            plainFormat = new MonetaryFormat().shift(0)
                    .minDecimals(0).repeatOptionalDecimals(1, unitExponent).noCode();
        }
        return plainFormat;
    }

    @Override
    public boolean equals(ValueType obj) {
        return super.equals(obj);
    }
}
