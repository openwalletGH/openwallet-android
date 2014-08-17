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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AddressStatus status1 = (AddressStatus) o;

        if (!address.equals(status1.address)) return false;
        if (status != null ? !status.equals(status1.status) : status1.status != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + (status != null ? status.hashCode() : 0);
        return result;
    }
}
