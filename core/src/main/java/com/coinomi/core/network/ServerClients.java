package com.coinomi.core.network;

import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletPocketHD;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 */
public class ServerClients {
    private static final Logger log = LoggerFactory.getLogger(ServerClient.class);

    private HashMap<CoinType, ServerClient> connections;

    public ServerClients(List<CoinAddress> coins, Wallet wallet) {
        // Supply a dumb ConnectivityHelper that reports that connection is always available
        this(coins, wallet, new ConnectivityHelper() {
            @Override
            public boolean isConnected() {
                return true;
            }
        });
    }

    public ServerClients(List<CoinAddress> coins, Wallet wallet, ConnectivityHelper connectivityHelper) {
        connections = new HashMap<CoinType, ServerClient>(coins.size());

        for (CoinAddress coinAddress : coins) {
            ServerClient client = new ServerClient(coinAddress, connectivityHelper);
            connections.put(coinAddress.getType(), client);
        }

        setPockets(wallet.getAllAccounts());
    }

    private void setPockets(List<WalletAccount> pockets) {
        for (WalletAccount pocket : pockets) {
            ServerClient connection = connections.get(pocket.getCoinType());
            if (connection == null) continue;
            connection.addEventListener(pocket);
        }
    }

    public void resetAccount(WalletAccount account) {
        ServerClient connection = connections.get(account.getCoinType());
        if (connection == null) return;
        connection.addEventListener(account);
        connection.resetConnection();
    }

    public void startAllAsync() {
        for (ServerClient client : connections.values()) {
            client.startAsync();
        }
    }

    public void startAsync(WalletAccount pocket) {
        if (pocket == null) {
            log.warn("Provided wallet account is null, not doing anything");
            return;
        }
        CoinType type = pocket.getCoinType();
        if (connections.containsKey(type)) {
            ServerClient c = connections.get(type);
            c.addEventListener(pocket);
            c.startAsync();
        } else {
            log.warn("No connection found for {}", type.getName());
        }
    }

    public void stopAllAsync() {
        for (ServerClient client : connections.values()) {
            client.stopAsync();
        }
    }

    public void ping() {
        for (final CoinType type : connections.keySet()) {
            ServerClient connection = connections.get(type);
            if (connection.isConnected()) connection.ping();
        }
    }

    public void resetConnections() {
        for (final CoinType type : connections.keySet()) {
            ServerClient connection = connections.get(type);
            if (connection.isConnected()) connection.resetConnection();
        }
    }
}
