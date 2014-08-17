package com.coinomi.core.network;

import com.google.bitcoin.core.Address;

import javax.annotation.Nullable;

/**
 * @author Giannis Dzegoutanis
 */
final public class AddressStatus {
    final Address address;
    @Nullable final String status;

    public AddressStatus(Address address, @Nullable String status) {
        this.address = address;
        this.status = status;
    }

    public Address getAddress() {
        return address;
    }

    @Nullable public String getStatus() {
        return status;
    }
}
