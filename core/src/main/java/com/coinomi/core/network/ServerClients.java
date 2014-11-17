package com.coinomi.core.network;

import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.stratumj.ServerAddress;
import com.coinomi.stratumj.StratumClient;
import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.ResultMessage;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 */
public class ServerClients {
    private static final Logger log = LoggerFactory.getLogger(ServerClient.class);

    private HashMap<CoinType, ServerClient> connections;

    public ServerClients(List<CoinAddress> coins, Wallet wallet) {
        connections = new HashMap<CoinType, ServerClient>(coins.size());

        for (CoinAddress coinAddress : coins) {
            ServerClient client = new ServerClient(coinAddress);
            connections.put(coinAddress.getType(), client);
        }

        setPockets(wallet.getPockets(), false);
    }

    public void setPockets(List<WalletPocket> pockets, boolean reconnect) {
        for (WalletPocket pocket : pockets) {
            connections.get(pocket.getCoinType()).setWalletPocket(pocket, reconnect);
        }
    }

    public void startAsync() {
        for (ServerClient client : connections.values()) {
            client.startAsync();
        }
    }

    public void stopAsync() {
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
}
