package com.coinomi.core.network;

import com.google.bitcoin.core.Address;

import javax.annotation.Nullable;

/**
 * @author Giannis Dzegoutanis
 */
final public class AddressStatus {
    final Address address;
    final String status;

    public AddressStatus(Address address, @Nullable String status) {
        this.address = address;
        this.status = status == null ? "" : status; // empty string for no status
    }

    public Address getAddress() {
        return address;
    }

    public String getStatus() {
        return status;
    }
}
