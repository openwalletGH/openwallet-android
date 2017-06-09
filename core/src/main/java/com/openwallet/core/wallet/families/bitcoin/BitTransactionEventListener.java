package com.openwallet.core.wallet.families.bitcoin;

import com.openwallet.core.network.AddressStatus;
import com.openwallet.core.network.ServerClient.UnspentTx;
import com.openwallet.core.network.interfaces.TransactionEventListener;

import java.util.List;

/**
 * @author John L. Jegutanis
 */
public interface BitTransactionEventListener extends TransactionEventListener<BitTransaction> {
    void onUnspentTransactionUpdate(AddressStatus status, List<UnspentTx> UnspentTxes);
}
