package com.coinomi.core;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.utils.Threading;
import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * @author Giannis Dzegoutanis
 *
 *
 */
public class WalletPocket implements TransactionEventListener, ConnectionEventListener, Serializable {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);
    private final ReentrantLock lock = Threading.lock("WalletPocket");

    private final CoinType coinType;
    private final HashMap<Address, String> addressStatus;
    private final ArrayList<ServerClient.Transaction> unspentTransactions;
    private final KeyChain keys;

    @Nullable private ServerClient serverClient;

    public WalletPocket(KeyChain keys, CoinType coinType) {
        this.keys = keys;
        this.coinType = coinType;
        addressStatus = new HashMap<Address, String>();
        unspentTransactions = new ArrayList<ServerClient.Transaction>();
    }


    private void updateAddressStatus(AddressStatus newStatus) {
        lock.lock();
        try {
            addressStatus.put(newStatus.getAddress(), newStatus.getStatus());
        }
        finally {
            lock.unlock();
        }
    }
    private boolean isAddressStatusChanged(AddressStatus status) {
        lock.lock();
        try {
            if (addressStatus.containsKey(status.getAddress())) {
                return addressStatus.get(status.getAddress()).equals(status.getStatus());
            }
            else {
                return true;
            }
        }
        finally {
            lock.unlock();
        }
    }

    List<Address> getWatchingAddresses() {
        ImmutableList.Builder<Address> addresses = ImmutableList.builder();
        for (ECKey key : keys.getLeafKeys()) {
            addresses.add(key.toAddress(coinType));
        }
        return addresses.build();
    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        lock.lock();
        try {
            if (isAddressStatusChanged(status)) {
                log.info("Must get UTXOs for address {}, status changes {}", status.getAddress(),
                        status.getStatus());
                if (serverClient != null) {
                    serverClient.getUnspentTx(coinType, status, this);
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    @Override
    public void onUnspentTransactionUpdate(AddressStatus status,
                                           List<ServerClient.Transaction> transactions) {
        log.info("Got {} unspent transactions for address {}", transactions.size(),
                status.getAddress());
        updateAddressStatus(status);
        for (ServerClient.Transaction tx : transactions) {
            log.info("- utxo {} worth {}", tx.getTxHash(), tx.getValue());
            unspentTransactions.add(tx);
        }
    }

    @Override
    public void onConnection(ServerClient serverClient) {
        this.serverClient = serverClient;
        serverClient.subscribeToAddresses(coinType, getWatchingAddresses(), this);
    }

    @Override
    public void onDisconnect() {
        this.serverClient = null;
    }
}
