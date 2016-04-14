package com.coinomi.core.network;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;
import com.coinomi.core.wallet.families.bitcoin.BitBlockchainConnection;
import com.coinomi.core.wallet.families.bitcoin.BitTransaction;
import com.coinomi.core.wallet.families.bitcoin.BitTransactionEventListener;
import com.coinomi.stratumj.ServerAddress;
import com.coinomi.stratumj.StratumClient;
import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.ResultMessage;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Service;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;
import static com.google.common.util.concurrent.Service.State.NEW;

/**
 * @author John L. Jegutanis
 */
public class ServerClient implements BitBlockchainConnection {
    private static final Logger log = LoggerFactory.getLogger(ServerClient.class);

    private static final ScheduledThreadPoolExecutor connectionExec;
    private static final String CLIENT_PROTOCOL = "0.9";

    static {
        connectionExec = new ScheduledThreadPoolExecutor(1);
        // FIXME, causing a crash in old Androids
//        connectionExec.setRemoveOnCancelPolicy(true);
    }
    private static final Random RANDOM = new Random();

    private static final long MAX_WAIT = 16;
    private static final long CONNECTION_STABILIZATION = 30;
    private final ConnectivityHelper connectivityHelper;

    private CoinType type;
    private final ImmutableList<ServerAddress> addresses;
    private final HashSet<ServerAddress> failedAddresses;
    private ServerAddress lastServerAddress;
    private StratumClient stratumClient;
    private long retrySeconds = 0;
    private long reconnectAt = 0;
    private boolean stopped = false;

    private File cacheDir;
    private int cacheSize;

    // TODO, only one is supported at the moment. Change when accounts are supported.
    private transient CopyOnWriteArrayList<ListenerRegistration<ConnectionEventListener>> eventListeners;

    private void reschedule(Runnable r, long delay, TimeUnit unit) {
        connectionExec.remove(r);
        connectionExec.schedule(r, delay, unit);
    }

    private Runnable reconnectTask = new Runnable() {
        @Override
        public void run() {
            if (!stopped) {
                long reconnectIn = Math.max(reconnectAt - System.currentTimeMillis(), 0);
                // Check if we must reconnect in the next second
                if (reconnectIn < 1000) {
                    if (connectivityHelper.isConnected()) {
                        createStratumClient().startAsync();
                    } else {
                        // Start polling for connection to become available
                        reschedule(reconnectTask, 1, TimeUnit.SECONDS);
                    }
                } else {
                    reschedule(reconnectTask, reconnectIn, TimeUnit.MILLISECONDS);
                }
            } else {
                log.info("{} client stopped, aborting reconnect.", type.getName());
            }
        }
    };

    private Runnable connectionCheckTask = new Runnable() {
        @Override
        public void run() {
            if (isActivelyConnected()) {
                reconnectAt = 0;
                retrySeconds = 0;
            }
        }
    };

    private Service.Listener serviceListener = new Service.Listener() {
        @Override
        public void running() {
            // Check if connection is up as this event is fired even if there is no connection
            if (isActivelyConnected()) {
                log.info("{} client connected to {}", type.getName(), lastServerAddress);
                broadcastOnConnection();

                // Test that the connection is stable
                reschedule(connectionCheckTask, CONNECTION_STABILIZATION, TimeUnit.SECONDS);
            }
        }

        @Override
        public void terminated(Service.State from) {
            log.info("{} client stopped", type.getName());
            broadcastOnDisconnect();
            failedAddresses.add(lastServerAddress);
            lastServerAddress = null;
            stratumClient = null;
            // Try to restart
            if (!stopped) {
                log.info("Reconnecting {} in {} seconds", type.getName(), retrySeconds);
                connectionExec.remove(connectionCheckTask);
                connectionExec.remove(reconnectTask);
                if (retrySeconds > 0) {
                    reconnectAt = System.currentTimeMillis() + retrySeconds * 1000;
                    connectionExec.schedule(reconnectTask, retrySeconds, TimeUnit.SECONDS);
                } else {
                    connectionExec.execute(reconnectTask);
                }
            }
        }
    };

    public ServerClient(CoinAddress coinAddress, ConnectivityHelper connectivityHelper) {
        this.connectivityHelper = connectivityHelper;
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<ConnectionEventListener>>();
        failedAddresses = new HashSet<ServerAddress>();
        type = coinAddress.getType();
        addresses = ImmutableList.copyOf(coinAddress.getAddresses());

        createStratumClient();
    }

    private StratumClient createStratumClient() {
        checkState(stratumClient == null);
        lastServerAddress = getServerAddress();
        stratumClient = new StratumClient(lastServerAddress);
        stratumClient.addListener(serviceListener, Threading.USER_THREAD);
        return stratumClient;
    }

    private ServerAddress getServerAddress() {
        // If we blacklisted all servers, reset
        if (failedAddresses.size() == addresses.size()) {
            failedAddresses.clear();
        }
        retrySeconds = Math.min(Math.max(1, retrySeconds * 2), MAX_WAIT);

        ServerAddress address;
        // Not the most efficient, but does the job
        while (true) {
            address = addresses.get(RANDOM.nextInt(addresses.size()));
            if (!failedAddresses.contains(address)) break;
        }
        return address;
    }

    public void startAsync() {
        if (stratumClient == null){
            log.info("Forcing service start");
            connectionExec.remove(reconnectTask);
            createStratumClient();
        }

        Service.State state = stratumClient.state();
        if (state != NEW || stopped) {
            log.debug("Not starting service as it is already started or explicitly stopped");
            return;
        }

        try {
            stratumClient.startAsync();
        } catch (IllegalStateException e) {
            // This can happen if the service has already been started or stopped (e.g. by another
            // service or listener). Our contract says it is safe to call this method if
            // all services were NEW when it was called, and this has already been verified above, so we
            // don't propagate the exception.
            log.warn("Unable to start Service " + type.getName(), e);
        }
    }

    public void stopAsync() {
        if (stopped) return;
        stopped = true;
        if (isActivelyConnected()) broadcastOnDisconnect();
        eventListeners.clear();
        connectionExec.remove(reconnectTask);
        if (stratumClient != null) {
            stratumClient.stopAsync();
            stratumClient = null;
        }
    }

    public boolean isActivelyConnected() {
        return stratumClient != null && stratumClient.isConnected() && stratumClient.isRunning();
    }

//    // TODO support more than one pocket
//    public void maybeSetWalletPocket(WalletPocketHD pocket) {
//        if (eventListeners.isEmpty()) {
//            setWalletPocket(pocket, false);
//        }
//    }
//
//    // TODO support more than one pocket
//    public void setWalletPocket(WalletPocketHD pocket, boolean reconnect) {
//        if (isActivelyConnected()) broadcastOnDisconnect();
//        eventListeners.clear();
//        addEventListener(pocket);
//        if (reconnect && isActivelyConnected()) {
//            resetConnection();
//            // will broadcast event on reconnect
//        } else {
//            if (isActivelyConnected()) broadcastOnConnection();
//        }
//    }

    /**
     * Will disconnect from the server and immediately will try to reconnect
     */
    public void resetConnection() {
        if (stratumClient != null) {
            stratumClient.disconnect();
        }
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like new connection to a server. The listener is executed by {@link org.bitcoinj.utils.Threading#USER_THREAD}.
     */
    @Override
    public void addEventListener(ConnectionEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like new connection to a server. The listener is executed by the given executor.
     */
    private void addEventListener(ConnectionEventListener listener, Executor executor) {
        boolean isNew = !ListenerRegistration.removeFromList(listener, eventListeners);
        eventListeners.add(new ListenerRegistration<ConnectionEventListener>(listener, executor));
        if (isNew && isActivelyConnected()) {
            broadcastOnConnection();
        }
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

    private BlockHeader parseBlockHeader(CoinType type, JSONObject json) throws JSONException {
        return new BlockHeader(type, json.getLong("timestamp"), json.getInt("block_height"));
    }

    @Override
    public void subscribeToBlockchain(final TransactionEventListener listener) {
        checkNotNull(stratumClient);

        // TODO use TransactionEventListener directly because the current solution leaks memory
        StratumClient.SubscribeResultHandler blockchainHeaderHandler = new StratumClient.SubscribeResultHandler() {
            @Override
            public void handle(CallMessage message) {
                try {
                    BlockHeader header = parseBlockHeader(type, message.getParams().getJSONObject(0));
                    listener.onNewBlock(header);
                } catch (JSONException e) {
                    log.error("Unexpected JSON format", e);
                }
            }
        };

        log.info("Going to subscribe to block chain headers");

        final CallMessage callMessage = new CallMessage("blockchain.headers.subscribe", (List)null);
        ListenableFuture<ResultMessage> reply = stratumClient.subscribe(callMessage, blockchainHeaderHandler);

        Futures.addCallback(reply, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(ResultMessage result) {
                try {
                    BlockHeader header = parseBlockHeader(type, result.getResult().getJSONObject(0));
                    listener.onNewBlock(header);
                } catch (JSONException e) {
                    log.error("Unexpected JSON format", e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof CancellationException) {
                    log.debug("Canceling {} call", callMessage.getMethod());
                } else {
                    log.error("Could not get reply for {} blockchain headers subscribe: {}",
                            type.getName(), t.getMessage());
                }
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void subscribeToAddresses(List<AbstractAddress> addresses, final TransactionEventListener<BitTransaction> listener) {
        checkNotNull(stratumClient);

        final CallMessage callMessage = new CallMessage("blockchain.address.subscribe", (List)null);

        // TODO use TransactionEventListener directly because the current solution leaks memory
        StratumClient.SubscribeResultHandler addressHandler = new StratumClient.SubscribeResultHandler() {
            @Override
            public void handle(CallMessage message) {
                try {
                    AbstractAddress address = BitAddress.from(type, message.getParams().getString(0));
                    AddressStatus status;
                    if (message.getParams().isNull(1)) {
                        status = new AddressStatus(address, null);
                    }
                    else {
                        status = new AddressStatus(address, message.getParams().getString(1));
                    }
                    listener.onAddressStatusUpdate(status);
                } catch (AddressMalformedException e) {
                    log.error("Address subscribe sent a malformed address", e);
                } catch (JSONException e) {
                    log.error("Unexpected JSON format", e);
                }
            }
        };

        for (final AbstractAddress address : addresses) {
            log.debug("Going to subscribe to {}", address);
            callMessage.setParam(address.toString());

            ListenableFuture<ResultMessage> reply = stratumClient.subscribe(callMessage, addressHandler);

            Futures.addCallback(reply, new FutureCallback<ResultMessage>() {

                @Override
                public void onSuccess(ResultMessage result) {
                    AddressStatus status = null;
                    try {
                        if (result.getResult().isNull(0)) {
                            status = new AddressStatus(address, null);
                        } else {
                            status = new AddressStatus(address, result.getResult().getString(0));
                        }
                        listener.onAddressStatusUpdate(status);
                    } catch (JSONException e) {
                        log.error("Unexpected JSON format", e);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    if (t instanceof CancellationException) {
                        log.info("Canceling {} call", callMessage.getMethod());
                    } else {
                        log.error("Could not get reply for {} address subscribe {}: ",
                                type.getName(), address, t.getMessage());
                    }
                }
            }, Threading.USER_THREAD);
        }
    }

    @Override
    public void getUnspentTx(final AddressStatus status,
                             final BitTransactionEventListener listener) {
        checkNotNull(stratumClient);

        CallMessage message = new CallMessage("blockchain.address.listunspent",
                Arrays.asList(status.getAddress().toString()));
        final ListenableFuture<ResultMessage> result = stratumClient.call(message);

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
    public void getHistoryTx(final AddressStatus status,
                             final TransactionEventListener<BitTransaction> listener) {
        checkNotNull(stratumClient);

        final CallMessage message = new CallMessage("blockchain.address.get_history",
                Arrays.asList(status.getAddress().toString()));
        final ListenableFuture<ResultMessage> result = stratumClient.call(message);

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
                if (t instanceof CancellationException) {
                    log.debug("Canceling {} call", message.getMethod());
                } else {
                    log.error("Could not get reply for blockchain.address.get_history", t);
                }
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void getTransaction(final Sha256Hash txHash,
                               final TransactionEventListener<BitTransaction> listener) {

        if (cacheDir != null) {
            Threading.USER_THREAD.execute(new Runnable() {
                @Override
                public void run() {
                    File txCachedFile = getTxCacheFile(txHash);
                    if (txCachedFile.exists()) {
                        try {
                            byte[] txBytes = Files.toByteArray(txCachedFile);
                            BitTransaction tx = new BitTransaction(type, txBytes);
                            if (!tx.getHash().equals(txHash)) {
                                if (!txCachedFile.delete()) {
                                    log.warn("Error deleting cached transaction {}", txCachedFile);
                                }
                            } else {
                                listener.onTransactionUpdate(tx);
                                return;
                            }
                        } catch (IOException e) {
                            log.warn("Error reading cached transaction", e);
                        }
                    }
                    // Fallback to fetching from the network
                    getTransactionFromNetwork(txHash, listener);
                }
            });
        } else {
            // Caching disabled, fetch from network
            getTransactionFromNetwork(txHash, listener);
        }
    }

    private File getTxCacheFile(Sha256Hash txHash) {
        return new File(new File(checkNotNull(cacheDir), type.getId()), txHash.toString());
    }

    private void getTransactionFromNetwork(final Sha256Hash txHash, final TransactionEventListener<BitTransaction> listener) {
        checkNotNull(stratumClient);

        final CallMessage message = new CallMessage("blockchain.transaction.get", txHash.toString());

        final ListenableFuture<ResultMessage> result = stratumClient.call(message);

        Futures.addCallback(result, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(ResultMessage result) {
                try {
                    String rawTx = result.getResult().getString(0);
                    byte[] txBytes = Utils.HEX.decode(rawTx);
                    BitTransaction tx = new BitTransaction(type, txBytes);
                    if (!tx.getHash().equals(txHash)) {
                        throw new Exception("Requested TX " + txHash + " but got " + tx.getHashAsString());
                    }
                    listener.onTransactionUpdate(tx);
                    if (cacheDir != null) {
                        try {
                            Files.write(txBytes, getTxCacheFile(txHash));
                        } catch (IOException e) {
                            log.warn("Error writing cached transaction", e);
                        }
                    }
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof CancellationException) {
                    log.debug("Canceling {} call", message.getMethod());
                } else {
                    log.error("Could not get reply for blockchain.transaction.get", t);
                }
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void getBlock(final int height, final TransactionEventListener<BitTransaction> listener) {
        checkNotNull(stratumClient);

        final CallMessage message = new CallMessage("blockchain.block.get_header", height);

        final ListenableFuture<ResultMessage> result = stratumClient.call(message);

        Futures.addCallback(result, new FutureCallback<ResultMessage>() {
            @Override
            public void onSuccess(ResultMessage result) {
                try {
                    BlockHeader header = parseBlockHeader(type, result.getResult().getJSONObject(0));
                    listener.onBlockUpdate(header);
                } catch (JSONException e) {
                    log.error("Unexpected JSON format", e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof CancellationException) {
                    log.debug("Canceling {} call", message.getMethod());
                } else {
                    log.error("Could not get reply for blockchain.block.get_header", t);
                }
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public void broadcastTx(final BitTransaction tx,
                            @Nullable final TransactionEventListener<BitTransaction> listener) {
        checkNotNull(stratumClient);

        CallMessage message = new CallMessage("blockchain.transaction.broadcast",
                Arrays.asList(Utils.HEX.encode(tx.bitcoinSerialize())));
        final ListenableFuture<ResultMessage> result = stratumClient.call(message);

        Futures.addCallback(result, new FutureCallback<ResultMessage>() {

            @Override
            public void onSuccess(ResultMessage result) {
                try {
                    String txId = result.getResult().getString(0);

                    // FIXME could return {u'message': u'', u'code': -25}
                    log.info("got tx {} =?= {}", txId, tx.getHash());
                    checkState(tx.getHash().toString().equals(txId));

                    if (listener != null) listener.onTransactionBroadcast(tx);
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Could not get reply for blockchain.transaction.broadcast", t);
                if (listener != null) listener.onTransactionBroadcastError(tx);
            }
        }, Threading.USER_THREAD);
    }

    @Override
    public boolean broadcastTxSync(final BitTransaction tx) {
        checkNotNull(stratumClient);

        CallMessage message = new CallMessage("blockchain.transaction.broadcast",
                Arrays.asList(Utils.HEX.encode(tx.bitcoinSerialize())));

        try {
            ResultMessage result = stratumClient.call(message).get();
            String txId = result.getResult().getString(0);

            // FIXME could return {u'message': u'', u'code': -25}
            log.info("got tx {} =?= {}", txId, tx.getHash());
            checkState(tx.getHash().toString().equals(txId));
            return true;
        } catch (Exception e) {
            log.error("Could not get reply for blockchain.transaction.broadcast", e);
        }
        return false;
    }

    @Override
    public void ping(@Nullable String versionString) {
        if (!isActivelyConnected()) {
            log.warn("There is no connection with {} server, skipping ping.", type.getName());
            return;
        }

        if (versionString == null) {
            versionString = this.getClass().getCanonicalName();
        }

        final CallMessage pingMsg = new CallMessage("server.version",
                ImmutableList.of(versionString, CLIENT_PROTOCOL));
        ListenableFuture<ResultMessage> pong = stratumClient.call(pingMsg);
        Futures.addCallback(pong, new FutureCallback<ResultMessage>() {
            @Override
            public void onSuccess(@Nullable ResultMessage result) {
                if (log.isDebugEnabled()) {
                    try {
                        log.debug("Server {} version {} OK", type.getName(),
                                checkNotNull(result).getResult().get(0));
                    } catch (Exception ignore) { }
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof CancellationException) {
                    log.debug("Canceling {} call", pingMsg.getMethod());
                } else {
                    log.error("Server {} ping failed", type.getName());
                }
            }
        }, Threading.USER_THREAD);
    }

    public void setCacheDir(File cacheDir, int cacheSize) {
        this.cacheDir = cacheDir;
        this.cacheSize = cacheSize;
    }


    public static class HistoryTx {
        protected final Sha256Hash txHash;
        protected final int height;

        public HistoryTx(JSONObject json) throws JSONException {
            txHash = new Sha256Hash(json.getString("tx_hash"));
            height = json.getInt("height");
        }

        public HistoryTx(TransactionOutPoint txop, int height) {
            this.txHash = txop.getHash();
            this.height = height;
        }

        public static List<HistoryTx> historyFromArray(JSONArray jsonArray) throws JSONException {
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
        protected final int txPos;
        protected final long value;

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

        public static List<HistoryTx> unspentFromArray(JSONArray jsonArray) throws JSONException {
            ImmutableList.Builder<HistoryTx> list = ImmutableList.builder();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(new UnspentTx(jsonArray.getJSONObject(i)));
            }
            return list.build();
        }

        public int getTxPos() {
            return txPos;
        }

        public long getValue() {
            return value;
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
