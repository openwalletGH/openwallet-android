package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.wallet.AbstractAddress;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.WrongNetworkException;

/**
 * @author John L. Jegutanis
 */
public class BitAddress extends Address implements AbstractAddress {

    public BitAddress(Address address) {
        this(address.getParameters(), address.getHash160());
    }

    public BitAddress(NetworkParameters params, byte[] hash160) {
        super(params, hash160);
    }

    public BitAddress(NetworkParameters params, int version, byte[] hash160) throws WrongNetworkException {
        super(params, version, hash160);
    }

    public BitAddress(NetworkParameters params, String address) throws AddressFormatException {
        super(params, address);
    }
}
