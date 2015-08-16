package com.coinomi.core.coins.families;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.families.nxt.NxtFamilyAddress;


/**
 * @author John L. Jegutanis
 *
 * Coins that belong to this family are: NXT, Burst, etc
 */
public abstract class NxtFamily extends CoinType {
    public static final short DEFAULT_DEADLINE = 1440;

    {
        family = Families.NXT;
    }

    @Override
    public AbstractAddress newAddress(String addressStr) throws AddressMalformedException {
        return NxtFamilyAddress.fromString(this, addressStr);
    }
}