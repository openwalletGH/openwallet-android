package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.nxt.Account;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.Crypto;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.exceptions.AddressMalformedException;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
final public class NxtAddress implements AbstractAddress {
    private final CoinType type;
    private final byte[] publicKey;
    private final long accountId;
    private final String rsAccount;

    public NxtAddress(CoinType type, byte[] pubKey) {
        this.type = type;
        publicKey = pubKey;
        accountId = Account.getId(pubKey);
        rsAccount = Convert.rsAccount(type, accountId);
    }

    public NxtAddress(CoinType type, long accountId, String rsAccount) {
        this.type = type;
        publicKey = null;
        this.accountId = accountId;
        this.rsAccount = rsAccount;
    }

    public NxtAddress(CoinType type, String rsAccount) {
        this.type = type;
        publicKey = null;
        this.accountId = Convert.parseAccountId(type, rsAccount);
        this.rsAccount = rsAccount;
    }

    public NxtAddress(CoinType type, long accountId) {
        this.type = type;
        publicKey = null;
        this.accountId = accountId;
        this.rsAccount = Convert.rsAccount(type, accountId);
    }

    public static NxtAddress fromString(CoinType type, String address)
            throws AddressMalformedException {
        try {
            return new NxtAddress(type,
                    Crypto.rsDecode(address.replace(type.getAddressPrefix(), "")), address);
        } catch (Exception e) {
            throw new AddressMalformedException(e);
        }
    }

    @Nullable
    public byte[] getPublicKey() {
        return publicKey;
    }

    public long getAccountId() {
        return accountId;
    }

    public String getRsAccount() {
        return rsAccount;
    }

    @Override
    public CoinType getType() {
        return type;
    }

    @Override
    public String toString() {
        return getRsAccount();
    }

    @Override
    public long getId() {
        return accountId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NxtAddress that = (NxtAddress) o;

        if (accountId != that.accountId) return false;
        if (!type.equals(that.type)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (int) (accountId ^ (accountId >>> 32));
        return result;
    }
}
