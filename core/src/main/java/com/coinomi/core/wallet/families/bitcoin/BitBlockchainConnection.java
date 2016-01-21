package com.coinomi.core.wallet.families.bitcoin;

import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.interfaces.BlockchainConnection;

/**
 * @author John L. Jegutanis
 */
public interface BitBlockchainConnection extends BlockchainConnection<BitTransaction> {
    void getUnspentTx(AddressStatus status, BitTransactionEventListener listener);
}
