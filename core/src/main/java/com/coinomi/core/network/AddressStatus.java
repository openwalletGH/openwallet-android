package com.coinomi.core.network;

import com.google.bitcoin.core.Address;

/**
 * @author Giannis Dzegoutanis
 */
final public class AddressStatus {
    final Address address;
    final String status;

    public AddressStatus(Address address, String status) {
        this.address = address;
        this.status = status;
    }

    public Address getAddress() {
        return address;
    }

    public String getStatus() {
        return status;
    }
}
