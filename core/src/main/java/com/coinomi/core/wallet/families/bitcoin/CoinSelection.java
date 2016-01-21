package com.coinomi.core.wallet.families.bitcoin;


import org.bitcoinj.core.Coin;

import java.util.Collection;
import java.util.List;

/**
 * Represents the results of a {@link CoinSelector#select(Coin, List)} operation. A
 * coin selection represents a list of spendable transaction outputs that sum together to give valueGathered.
 * Different coin selections could be produced by different coin selectors from the same input set, according
 * to their varying policies.
 */
public class CoinSelection {
    public Coin valueGathered;
    public Collection<OutPointOutput> gathered;

    public CoinSelection(Coin valueGathered, Collection<OutPointOutput> gathered) {
        this.valueGathered = valueGathered;
        this.gathered = gathered;
    }
}
