package com.coinomi.core.network.interfaces;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.wallet.WalletPocket;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import java.util.List;

/**
 * @author Giannis Dzegoutanis
 */
public interface BlockchainConnection {
    void subscribeToAddresses(List<Address> addresses,
                              TransactionEventListener listener);

    void getUnspentTx(AddressStatus status, TransactionEventListener listener);

    void getHistoryTx(AddressStatus status, TransactionEventListener listener);

    void getTransaction(Sha256Hash txHash, TransactionEventListener listener);

    void broadcastTx(final Transaction tx, final TransactionEventListener listener);

    void ping();

}
