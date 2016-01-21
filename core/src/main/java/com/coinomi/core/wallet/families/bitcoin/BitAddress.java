package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.wallet.AbstractAddress;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.WrongNetworkException;
import org.bitcoinj.script.Script;

import java.nio.ByteBuffer;

/**
 * @author John L. Jegutanis
 */
public class BitAddress extends Address implements AbstractAddress {

    BitAddress(Address address) throws WrongNetworkException {
        this((CoinType) address.getParameters(), address.getVersion(), address.getHash160());
    }

    BitAddress(CoinType type, byte[] hash160) {
        super(type, hash160);
    }

    BitAddress(CoinType type, int version, byte[] hash160) throws WrongNetworkException {
        super(type, version, hash160);
    }

    BitAddress(CoinType type, String address) throws AddressFormatException {
        super(type, address);
    }

    public static BitAddress from(CoinType type, String address) throws AddressMalformedException {
        try {
            return new BitAddress(type, address);
        } catch (AddressFormatException e) {
            throw new AddressMalformedException(e);
        }
    }

    public static BitAddress from(CoinType type, int version, byte[] hash160)
            throws AddressMalformedException {
        try {
            return new BitAddress(type, version, hash160);
        } catch (WrongNetworkException e) {
            throw new AddressMalformedException(e);
        }
    }

    public static BitAddress from(CoinType type, byte[] publicKeyHash160)
            throws AddressMalformedException {
        try {
            return new BitAddress(type, type.getAddressHeader(), publicKeyHash160);
        } catch (WrongNetworkException e) {
            throw new AddressMalformedException(e);
        }
    }

    public static BitAddress from(CoinType type, Script script) throws AddressMalformedException {
        try {
            return new BitAddress(script.getToAddress(type));
        } catch (WrongNetworkException e) {
            throw new AddressMalformedException(e);
        }
    }

    public static BitAddress from(CoinType type, ECKey key) {
        return new BitAddress(type, key.getPubKeyHash());
    }

    public static BitAddress from(AbstractAddress address) throws AddressMalformedException {
        try {
            if (address instanceof BitAddress) {
                return (BitAddress) address;
            } else if (address instanceof Address) {
                return new BitAddress((Address) address);
            } else {
                return new BitAddress(address.getType(), address.toString());
            }
        } catch (AddressFormatException e) {
            throw new AddressMalformedException(e);
        }
    }

    public static BitAddress from(Address address) throws AddressMalformedException {
        try {
            return new BitAddress(address);
        } catch (WrongNetworkException e) {
            throw new AddressMalformedException(e);
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
