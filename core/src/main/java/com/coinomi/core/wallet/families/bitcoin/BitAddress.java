package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.exceptions.AddressMalformedException;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.WrongNetworkException;
import org.bitcoinj.script.Script;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author John L. Jegutanis
 */
public class BitAddress extends Address implements AbstractAddress {

    public BitAddress(Address address) {
        this((CoinType) address.getParameters(), address.getHash160());
    }

    public BitAddress(CoinType type, byte[] hash160) {
        super(type, hash160);
    }

    public BitAddress(CoinType type, int version, byte[] hash160) throws WrongNetworkException {
        super(type, version, hash160);
    }

    public BitAddress(CoinType type, String address) throws AddressFormatException {
        super(type, address);
    }

    public static BitAddress fromP2SHScript(CoinType type, Script scriptPubKey) throws WrongNetworkException {
        checkArgument(scriptPubKey.isPayToScriptHash(), "Not a P2SH script");
        return new BitAddress(type, type.getP2SHHeader(), scriptPubKey.getPubKeyHash());
    }

    public static BitAddress fromString(CoinType type, String address)
            throws AddressMalformedException {
        try {
            return new BitAddress(type, address);
        } catch (AddressFormatException e) {
            throw new AddressMalformedException(e);
        }
    }

    public static BitAddress fromKey(CoinType type, ECKey key) {
        return new BitAddress(type, key.getPubKeyHash());
    }

    public static BitAddress fromAbstractAddress(AbstractAddress address) throws AddressFormatException {
        if (address instanceof BitAddress) {
            return (BitAddress) address;
        } else if (address instanceof Address) {
            return new BitAddress(address.getType(), ((Address) address).getHash160());
        } else {
            return new BitAddress(address.getType(), address.toString());
        }
    }

    @Override
    public CoinType getType() {
        return (CoinType) getParameters();
    }

    @Override
    public long getId() {
        return ByteBuffer.wrap(getHash160()).getLong();
    }
}
