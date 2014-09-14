package com.coinomi.core.network;

import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.stratumj.ServerAddress;
import com.coinomi.stratumj.StratumClient;
import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.ResultMessage;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionOutPoint;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.utils.ListenerRegistration;
import com.google.bitcoin.utils.Threading;
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
public class ServerClient implements BlockchainConnection {
    private static final Logger log = LoggerFactory.getLogger(ServerClient.class);

    private HashBiMap<CoinType, StratumClient> connections;

    final ServiceManager manager;
    private Wallet wallet;

    private transient CopyOnWriteArrayList<ListenerRegistration<ConnectionEventListener>> eventListeners;

    public ServerClient(List<CoinAddress> coins) {
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<ConnectionEventListener>>();
        connections = HashBiMap.create(coins.size());


        for (CoinAddress coinAddress : coins) {
            List<ServerAddress> addresses = coinAddress.getAddresses();
            StratumClient client = new StratumClient(addresses);
            connections.put(coinAddress.getParameters(), client);
        }

        manager = new ServiceManager(connections.values());
        ServiceManager.Listener managerListener = new ServiceManager.Listener() {
            public void stopped() {
                log.info("All coin clients stopped");
                broadcastOnDisconnect();
            }

            public void healthy() {
                log.info("All coin clients are running");
                broadcastOnConnection();
            }

            public void failure(Service service) {
                StratumClient client = (StratumClient) service;

                log.error("Client failed: " + connections.inverse().get(client));

                // TODO try to reconnect
            }
        };

        manager.addListener(managerListener, Threading.SAME_THREAD);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                // Give the services 5 seconds to stop to ensure that we are responsive to shutdown
                // requests.
                try {
                    manager.stopAsync().awaitStopped(5, TimeUnit.SECONDS);
                } catch (TimeoutException timeout) {
                    // stopping timed out
                }
            }
        });
    }

    /**
     * Returns true if all services are currently in running.
     */
    public boolean isHealthy() {
        return manager.isHealthy();
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like new connection to a server. The listener is executed by the given executor.
     */
    public void addEventListener(ConnectionEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like new connection to a server. The listener is executed by the given executor.
     */
    public void addEventListener(ConnectionEventListener listener, Executor executor) {
        eventListeners.add(new ListenerRegistration<ConnectionEventListener>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeEventListener(ConnectionEventListener listener) {
        return ListenerRegistration.removeFromList(listener, eventListeners);
    }

    private void broadcastOnConnection() {
        for (final ListenerRegistration<ConnectionEventListener> registration : eventListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onConnection(ServerClient.this);
                }
            });
        }
    }

    private void broadcastOnDisconnect() {
        for (final ListenerRegistration<ConnectionEventListener> registration : eventListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onDisconnect();
                }
            });
        }
    }

    public void addWallet(Wallet wallet) {
        this.wallet = wallet;
        addEventListener(wallet);
    }

    public void startAsync() {
        manager.startAsync();
    }

    public void stopAsync() {
        manager.stopAsync();
    }


    @Override
    public void subscribeToAddresses(final CoinType coin, List<Address> addresses,
                                     final TransactionEventListener listener) {

        StratumClient client = checkNotNull(connections.get(coin));

        CallMessage callMessage = new CallMessage("blockchain.address.subscribe", (List)null);

        // TODO use TransactionEventListener directly because the current solution leaks memory
        StratumClient.SubscribeResult addressHandler = new StratumClient.SubscribeResult() {
            @Override
            public void handle(CallMessage message) {
                try {
                    Address address = new Address(coin, message.getParams().getString(0));
                    AddressStatus status;
                    if (message.getParams().isNull(1)) {
                        status = new AddressStatus(address, null);
                    }
                    else {
                        status = new AddressStatus(address, message.getParams().getString(1));
                    }
                    listener.onAddressStatusUpdate(status);
                } catch (AddressFormatException e) {
                    log.error("Address subscribe sent a malformed address", e);
                } catch (JSONException e) {
                    log.error("Unexpected JSON format", e);
                }
            }
        };

        for (final Address address : addresses) {
            log.info("Going to subscribe to {}", address);
            callMessage.setParam(address.toString());

            ListenableFuture<ResultMessage> reply = client.subscribe(callMessage, addressHandler);

            Futures.addCallback(reply, new FutureCallback<ResultMessage>() {

                @Override
                public void onSuccess(ResultMessage result) {
                    AddressStatus status = null;
                    try {
                        if (result.getResult().isNull(0)) {
                            status = new AddressStatus(address, null);
                        }
                        else {
                            status = new AddressStatus(address, result.getResult().getString(0));
                        }
                        listener.onAddressStatusUpdate(status);
                    } catch (JSONException e) {
                        log.error("Unexpected JSON format", e);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Could not get reply for address subscribe", t);
                }
            }, Threading.USER_THREAD);
        }
    }

    @Override
    public void getUnspentTx(final CoinType coinType, final AddressStatus status,
                             final TransactionEventListener listener) {

        StratumClient client = checkNotNull(connections.get(coinType));

        CallMessage message = new CallMessage("blockchain.address.listunspent",
                Arrays.asList(status.getAddress().toString()));
        final ListenableFuture<ResultMessage> result = client.call(message);

        Futures.addCallback(result, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(ResultMessage result) {
                JSONArray resTxs = result.getResult();
                ImmutableList.Builder<UnspentTx> utxes = ImmutableList.builder();
                try {
                    for (int i = 0; i < resTxs.length(); i++) {
                        utxes.add(new UnspentTx(resTxs.getJSONObject(i)));
                    }
                } catch (JSONException e) {
                    onFailure(e);
                    return;
                }
                listener.onUnspentTransactionUpdate(status, utxes.build());
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Could not get reply for blockchain.address.listunspent", t);
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void getHistoryTx(final CoinType coinType, final AddressStatus status,
                             final TransactionEventListener listener) {
        StratumClient client = checkNotNull(connections.get(coinType));

        CallMessage message = new CallMessage("blockchain.address.get_history",
                Arrays.asList(status.getAddress().toString()));
        final ListenableFuture<ResultMessage> result = client.call(message);

        Futures.addCallback(result, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(ResultMessage result) {
                JSONArray resTxs = result.getResult();
                ImmutableList.Builder<HistoryTx> historyTxs = ImmutableList.builder();
                try {
                    for (int i = 0; i < resTxs.length(); i++) {
                        historyTxs.add(new HistoryTx(resTxs.getJSONObject(i)));
                    }
                } catch (JSONException e) {
                    onFailure(e);
                    return;
                }
                listener.onTransactionHistory(status, historyTxs.build());
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Could not get reply for blockchain.address.get_history", t);
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void getTransaction(final CoinType coinType, final Sha256Hash txHash, final TransactionEventListener listener) {
        // {"params": ["a52418acead4fbc25252cba18f26de88166ef065e7237200253d27ef7ca53505"], "id": 27, "method": "blockchain.transaction.get"}

        StratumClient client = checkNotNull(connections.get(coinType));

        CallMessage message = new CallMessage("blockchain.transaction.get",
                Arrays.asList(txHash.toString()));
        final ListenableFuture<ResultMessage> result = client.call(message);

        Futures.addCallback(result, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(ResultMessage result) {
                try {
                    String rawTx = result.getResult().getString(0);
                    Transaction tx = new Transaction(coinType, Utils.HEX.decode(rawTx));
                    listener.onTransactionUpdate(tx);
                } catch (Exception e) {
                    onFailure(e);
                    return;
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Could not get reply for blockchain.transaction.get", t);
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void broadcastTx(CoinType coinType, final Transaction tx, final TransactionEventListener listener) {
        StratumClient client = checkNotNull(connections.get(coinType));

        CallMessage message = new CallMessage("blockchain.transaction.broadcast",
                Arrays.asList(Utils.HEX.encode(tx.bitcoinSerialize())));
        final ListenableFuture<ResultMessage> result = client.call(message);

        Futures.addCallback(result, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(ResultMessage result) {
                try {
                    String txId = result.getResult().getString(0);

                    // FIXME could crash due to transaction malleability
                    checkState(tx.getHash().toString().equals(txId));

                    listener.onTransactionBroadcast(tx);
                } catch (JSONException e) {
                    onFailure(e);
                    return;
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Could not get reply for blockchain.transaction.broadcast", t);
                listener.onTransactionBroadcastError(tx, t);
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void ping() {
        for (final CoinType type : connections.keySet()) {
            CallMessage pingMsg = new CallMessage("server.version", ImmutableList.of());
            ListenableFuture<ResultMessage> pong = connections.get(type).call(pingMsg);
            Futures.addCallback(pong, new FutureCallback<ResultMessage>() {
                @Override
                public void onSuccess(@Nullable ResultMessage result) {
                    try {
                        log.info("Server {} version {} OK", type.getName(),
                                result.getResult().get(0));
                    } catch (JSONException ignore) { }
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("Server {} ping failed", type.getName());
                }
            }, Threading.USER_THREAD);
        }
    }

    public static class HistoryTx {
        protected Sha256Hash txHash;
        protected int height;

        public HistoryTx(JSONObject json) throws JSONException {
            txHash = new Sha256Hash(json.getString("tx_hash"));
            height = json.getInt("height");
        }

        public HistoryTx(TransactionOutPoint txop, int height) {
            this.txHash = txop.getHash();
            this.height = height;
        }

        public static List<? extends HistoryTx> fromArray(JSONArray jsonArray) throws JSONException {
            ImmutableList.Builder<HistoryTx> list = ImmutableList.builder();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(new HistoryTx(jsonArray.getJSONObject(i)));
            }
            return list.build();
        }

        public Sha256Hash getTxHash() {
            return txHash;
        }

        public int getHeight() {
            return height;
        }
    }

    public static class UnspentTx extends HistoryTx {
        private int txPos;
        private long value;

        public UnspentTx(JSONObject json) throws JSONException {
            super(json);
            txPos = json.getInt("tx_pos");
            value = json.getLong("value");
        }

        public UnspentTx(TransactionOutPoint txop, long value, int height) {
            super(txop, height);
            this.txPos = (int) txop.getIndex();
            this.value = value;
        }

        public static List<? extends HistoryTx> fromArray(JSONArray jsonArray) throws JSONException {
            ImmutableList.Builder<UnspentTx> list = ImmutableList.builder();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(new UnspentTx(jsonArray.getJSONObject(i)));
            }
            return list.build();
        }

        public Sha256Hash getTxHash() {
            return txHash;
        }

        public int getTxPos() {
            return txPos;
        }

        public long getValue() {
            return value;
        }

        public int getHeight() {
            return height;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UnspentTx unspentTx = (UnspentTx) o;

            if (txPos != unspentTx.txPos) return false;
            if (value != unspentTx.value) return false;
            if (!txHash.equals(unspentTx.txHash)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = txHash.hashCode();
            result = 31 * result + txPos;
            result = 31 * result + (int) (value ^ (value >>> 32));
            return result;
        }
    }
}
