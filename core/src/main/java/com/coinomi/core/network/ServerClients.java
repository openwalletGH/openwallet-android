package com.coinomi.core.network;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletAccount;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * @author John L. Jegutanis
 */
public class ServerClients {
    private static final Logger log = LoggerFactory.getLogger(ServerClient.class);
    private final ConnectivityHelper connectivityHelper;
    private HashMap<CoinType, ServerClient> connections = new HashMap<>();
    private HashMap<CoinType, CoinAddress> addresses = new HashMap<>();


    private static ConnectivityHelper DEFAULT_CONNECTIVITY_HELPER = new ConnectivityHelper() {
        @Override
        public boolean isConnected() { return true; }
    };

    public ServerClients(List<CoinAddress> coins) {
        // Supply a dumb ConnectivityHelper that reports that connection is always available
        this(coins, DEFAULT_CONNECTIVITY_HELPER);
    }

    public ServerClients(List<CoinAddress> coinAddresses, ConnectivityHelper connectivityHelper) {
        this.connectivityHelper = connectivityHelper;
        setupAddresses(coinAddresses);
    }

    private void setupAddresses(List<CoinAddress> coins) {
        for (CoinAddress coinAddress : coins) {
            addresses.put(coinAddress.getType(), coinAddress);
        }
    }

    public void resetAccount(WalletAccount account) {
        ServerClient connection = connections.get(account.getCoinType());
        if (connection == null) return;
        connection.addEventListener(account);
        connection.resetConnection();
    }

    public void startAsync(WalletAccount pocket) {
        if (pocket == null) {
            log.warn("Provided wallet account is null, not doing anything");
            return;
        }
        CoinType type = pocket.getCoinType();
        ServerClient connection = getConnection(type);
        connection.addEventListener(pocket);
        connection.startAsync();
    }

    private ServerClient getConnection(CoinType type) {
        if (connections.containsKey(type)) return connections.get(type);
        // Try to create a connection
        if (addresses.containsKey(type)) {
            ServerClient client = new ServerClient(addresses.get(type), connectivityHelper);
            connections.put(type, client);
            return client;
        } else {
            // Should not happen
            throw new RuntimeException("Tried to create connection for an unknown server.");
        }
    }

    public void stopAllAsync() {
        for (ServerClient client : connections.values()) {
            client.stopAsync();
        }
        connections.clear();
    }

    public void ping() {
        for (final CoinType type : connections.keySet()) {
            ServerClient connection = connections.get(type);
            if (connection.isActivelyConnected()) connection.ping();
        }
    }

    public void resetConnections() {
        for (final CoinType type : connections.keySet()) {
            ServerClient connection = connections.get(type);
            if (connection.isActivelyConnected()) connection.resetConnection();
        }
    }
}
