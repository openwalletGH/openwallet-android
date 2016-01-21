package com.coinomi.core.wallet;

import com.coinomi.core.wallet.families.bitcoin.CoinSelection;
import com.coinomi.core.wallet.families.bitcoin.CoinSelector;
import com.coinomi.core.wallet.families.bitcoin.OutPointOutput;
import com.google.common.annotations.VisibleForTesting;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * @author John L. Jegutanis
 */



/**
 * This class implements a {@link CoinSelector} which attempts to get the highest priority
 * possible. This means that the transaction is the most likely to get confirmed. Note that this means we may end up
 * "spending" more priority than would be required to get the transaction we are creating confirmed.
 */
public class WalletCoinSelector implements CoinSelector {

    public CoinSelection select(Coin biTarget, List<OutPointOutput> candidates) {
        long target = biTarget.value;
        HashSet<OutPointOutput> selected = new HashSet<>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        // TODO: Consider changing the wallets internal format to track just outputs and keep them ordered.
        ArrayList<OutPointOutput> sortedOutputs = new ArrayList<>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        if (!biTarget.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        for (OutPointOutput output : sortedOutputs) {
            if (total >= target) break;
            // Only pick chain-included transactions, or transactions that are ours and pending.
            if (!shouldSelect(output)) continue;
            selected.add(output);
            total += output.getValueLong();
        }
        // Total may be lower than target here, if the given candidates were insufficient to create to requested
        // transaction.
        return new CoinSelection(Coin.valueOf(total), selected);
    }

    @VisibleForTesting
    static void sortOutputs(ArrayList<OutPointOutput> outputs) {
        Collections.sort(outputs, new Comparator<OutPointOutput>() {

            @Override
            public int compare(OutPointOutput a, OutPointOutput b) {
                int depth1 = a.getDepthInBlocks();
                int depth2 = b.getDepthInBlocks();
                long aValue = a.getValueLong();
                long bValue = b.getValueLong();
                BigInteger aCoinDepth = BigInteger.valueOf(aValue).multiply(BigInteger.valueOf(depth1));
                BigInteger bCoinDepth = BigInteger.valueOf(bValue).multiply(BigInteger.valueOf(depth2));
                int c1 = bCoinDepth.compareTo(aCoinDepth);
                if (c1 != 0) return c1;
                // The "coin*days" destroyed are equal, sort by value alone to get the lowest transaction size.
                if (aValue != bValue) return aValue > bValue ? 1 : -1;
                // They are entirely equivalent (possibly pending) so sort by hash to ensure a total ordering.
                BigInteger aHash = a.getTxHash().toBigInteger();
                BigInteger bHash = b.getTxHash().toBigInteger();
                return aHash.compareTo(bHash);
            }
        });
    }

    /** Sub-classes can override this to just customize whether transactions are usable, but keep age sorting. */
    protected boolean shouldSelect(OutPointOutput utxo) {
        return true; // Our unspent outputs are already filtered
    }
}
