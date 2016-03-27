package com.coinomi.core.network;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.coins.nxt.NxtException;
import com.coinomi.core.coins.nxt.TransactionImpl;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.network.interfaces.TransactionEventListener;

import com.coinomi.core.wallet.families.nxt.NxtTransaction;
import com.coinomi.stratumj.ServerAddress;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.FormEncodingBuilder;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.utils.ListenerRegistration;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;


/**
 * @author vbcs
 * @author John L. Jegutanis
 */
public class NxtServerClient implements BlockchainConnection<NxtTransaction> {

    private static final int POLL_INTERVAL_SEC = 30;

    private static final Logger log = LoggerFactory.getLogger(NxtServerClient.class);

    private static final ScheduledThreadPoolExecutor connectionExec;
    static {
        connectionExec = new ScheduledThreadPoolExecutor(1);
        // FIXME, causing a crash in old Androids
//        connectionExec.setRemoveOnCancelPolicy(true);
    }

    private static final Random RANDOM = new Random();

    private static final long MAX_WAIT = 16;
    private final ConnectivityHelper connectivityHelper;

    private CoinType type;
    private long retrySeconds = 0;
    private boolean stopped = false;
    private ServerAddress lastServerAddress;
    private final ImmutableList<ServerAddress> addresses;
    private final HashSet<ServerAddress> failedAddresses;

    private String lastBalance = "";
    private BlockHeader lastBlockHeader = new BlockHeader(type, 0, 0);

    private static final String GET_ACCOUNT = "getAccount";
    private static final String GET_EC_BLOCK = "getECBlock";
    private static final String GET_LAST_BLOCK = "getBlock";
    private static final String GET_REQUEST = "requestType=";
    private static final String GET_TRANSACTION = "getTransaction";
    private static final String GET_TRANSACTION_BYTES = "getTransactionBytes";
    private static final String GET_BLOCKCHAIN_TXS = "getBlockchainTransactions";

    private int ecBlockHeight = 0;
    private String ecBlockId = "";

    private OkHttpClient client;

    // TODO, only one is supported at the moment. Change when accounts are supported.
    private transient CopyOnWriteArrayList<ListenerRegistration<ConnectionEventListener>> eventListeners;
    private ScheduledExecutorService blockchainSubscription;
    private ScheduledExecutorService ecSubscription;
    private ScheduledExecutorService addressSubscription;

    public int getEcBlockHeight() { return ecBlockHeight; }

    public Long getEcBlockId() { return Convert.parseUnsignedLong(ecBlockId); }

    private Runnable reconnectTask = new Runnable() {
        public boolean isPolling = true;
        @Override
        public void run() {
            if (!stopped) {
                if (connectivityHelper.isConnected()) {
                    isPolling = false;
                } else {
                    // Start polling for connection to become available
                    if (!isPolling) log.info("No connectivity, starting polling.");
                    connectionExec.remove(reconnectTask);
                    connectionExec.schedule(reconnectTask, 10, TimeUnit.SECONDS);
                    isPolling = true;
                }
            } else {
                log.info("{} client stopped, aborting reconnect.", type.getName());
                isPolling = false;
            }
        }
    };


    public NxtServerClient(CoinAddress coinAddress, ConnectivityHelper connectivityHelper) {
        this.connectivityHelper = connectivityHelper;
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<ConnectionEventListener>>();
        failedAddresses = new HashSet<ServerAddress>();
        type = coinAddress.getType();
        addresses = ImmutableList.copyOf(coinAddress.getAddresses());
    }

    private static JSONObject parseReply(Response response) throws IOException, JSONException {
        return new JSONObject(response.body().string());
    }

    private String getBaseUrl() {
        ServerAddress address = getServerAddress();
        StringBuilder builder = new StringBuilder();
        builder.append("http://" + address.getHost()).append(":").append(address.getPort())
                .append("/nxt?");
        return builder.toString();
    }

    private String getAccountInfo(AbstractAddress address) {
        StringBuilder builder = new StringBuilder();
        builder.append(getBaseUrl()).append(GET_REQUEST).append(GET_ACCOUNT)
        .append("&account=").append(address.toString());
        return builder.toString();
    }

    private String getBlockchainStatusUrl() {
        ServerAddress address = getServerAddress();
        StringBuilder builder = new StringBuilder();
        builder.append("http://" + address.getHost()).append(":").append(address.getPort())
                .append("/nxt?").append(GET_REQUEST).append(GET_LAST_BLOCK);
        return builder.toString();
    }

    private ServerAddress getServerAddress() {
        // If we blacklisted all servers, reset and increase back-off time
        if (failedAddresses.size() == addresses.size()) {
            failedAddresses.clear();
            retrySeconds = Math.min(Math.max(1, retrySeconds * 2), MAX_WAIT);
        }

        ServerAddress address;
        // Not the most efficient, but does the job
        while (true) {
            address = addresses.get(RANDOM.nextInt(addresses.size()));
            if (!failedAddresses.contains(address)) break;
        }
        return address;
    }

    private OkHttpClient getHttpClient(){
        if (client == null) {
            client = new OkHttpClient();
        }
        return client;
    }

    private String getEcUrl() {
        return getBaseUrl() + GET_REQUEST + GET_EC_BLOCK;
    }

    private String getBlockChainTxsUrl(String address) {
        StringBuilder builder = new StringBuilder();
        builder.append(getBaseUrl()).append(GET_REQUEST).append(GET_BLOCKCHAIN_TXS)
                .append("&account=").append(address);
        return builder.toString();
    }

    private String getTransactionUrl(String txHash) {
        StringBuilder builder = new StringBuilder();
        builder.append(getBaseUrl()).append(GET_REQUEST).append(GET_TRANSACTION)
                .append("&fullHash=").append(txHash);
        return builder.toString();
    }

    private String getTransactionBytesUrl(String txId) {
        StringBuilder builder = new StringBuilder();
        builder.append(getBaseUrl()).append(GET_REQUEST).append(GET_TRANSACTION_BYTES)
                .append("&transaction=").append(txId);
        return builder.toString();
    }

    @Override
    public void subscribeToBlockchain(final TransactionEventListener listener) {

        log.info("Going to subscribe to block chain headers");
        if (blockchainSubscription != null) {
            blockchainSubscription.shutdownNow();
        }

        blockchainSubscription = Executors.newSingleThreadScheduledExecutor();
        blockchainSubscription.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {

                Request request = new Request.Builder().url(getBlockchainStatusUrl()).build();
                getHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        log.info("Failed to communicate with server:  " + request.toString());

                    }
                    @Override
                    public void onResponse(Response response) throws IOException {
                        try {
                            if (!response.isSuccessful()) {
                                log.info("Unable to fetch blockchain status.");
                                log.info("[Error code] = " + response.code() );
                            }
                            JSONObject reply = parseReply(response);
                            long timestamp = reply.getLong("timestamp");
                            int height = reply.getInt("height");
                            BlockHeader blockheader = new BlockHeader(type, timestamp, height);

                            if (!lastBlockHeader.equals(blockheader)) {
                                lastBlockHeader = blockheader;
                                listener.onNewBlock(blockheader);
                            }

                        } catch (IOException e) {
                            log.info("IOException: " + e.getMessage());
                        } catch (JSONException e) {
                            log.info("Could not parse JSON: " + e.getMessage());
                        }
                    }
                });

            }
        }, 0, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
        subscribeToEc();
    }

    /*
    Method to keep up to date ecBlockId and ecBlockHeight parameters ( runs every 15seconds )
     */
    private void subscribeToEc() {
        if (ecSubscription != null) {
            ecSubscription.shutdownNow();
        }

        ecSubscription = Executors.newSingleThreadScheduledExecutor();
        ecSubscription.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {

                Request request = new Request.Builder().url(getEcUrl()).build();
                getHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        log.info("Failed to communicate with server:  " + request.toString());

                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        try {
                            if (!response.isSuccessful()) {
                                log.info("Unable to fetch EC block.");
                                log.info("[Error code] = " + response.code());
                            }
                            JSONObject reply = parseReply(response);
                            ecBlockHeight = reply.getInt("ecBlockHeight");
                            ecBlockId = reply.getString("ecBlockId");

                        } catch (IOException e) {
                            log.info("IOException: " + e.getMessage());
                        } catch (JSONException e) {
                            log.info("Could not parse JSON: " + e.getMessage());
                        }
                    }
                });


            }
        }, 0, POLL_INTERVAL_SEC, TimeUnit.SECONDS);

    }

    @Override
    public void getBlock(int height, TransactionEventListener<NxtTransaction> listener) {
        throw new RuntimeException("NxtServerClient::getBlock not implemented");
    }

    /*
        Method that monitors account's unconfirmed balance.
        Raises onAddressStatusUpdate if changed.
         */
    @Override
    public void subscribeToAddresses(List<AbstractAddress> addresses,
                                     final TransactionEventListener<NxtTransaction> listener) {
        if (addressSubscription != null) {
            addressSubscription.shutdownNow();
        }
        addressSubscription = Executors.newSingleThreadScheduledExecutor();
        for (final AbstractAddress address : addresses) {
            log.info("Going to subscribe to {}", address);

            addressSubscription.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {

                    Request request = new Request.Builder().url(getAccountInfo(address)).build();
                    getHttpClient().newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Request request, IOException e) {
                            log.info("Failed to communicate with server:  " + request.toString());

                        }

                        @Override
                        public void onResponse(Response response) throws IOException {
                            try {
                                if (!response.isSuccessful()) {
                                    log.info("Unable to check address status.");
                                    log.info("[Error code] = " + response.code() );
                                }
                                JSONObject reply = parseReply(response);
                                String status = reply.getString("unconfirmedBalanceNQT");
                                AddressStatus addressStatus = new AddressStatus(address,status);

                                if (!lastBalance.equals(status)) {
                                    lastBalance = status;
                                    listener.onAddressStatusUpdate(addressStatus);
                                }
                                //listener.onAddressStatusUpdate(addressStatus);

                            } catch (IOException e) {
                                log.info("IOException: " + e.getMessage());
                            } catch (JSONException e) {
                                log.info("Could not parse JSON: " + e.getMessage());
                            }
                        }
                    });
                }
            }, 0, POLL_INTERVAL_SEC, TimeUnit.SECONDS);

        }
    }

    /*
        Method that fetches all account's transactions.
        Only call it when account's balance has changed
     */
    @Override
    public void getHistoryTx(final AddressStatus status, final TransactionEventListener listener) {

        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                log.info("Going to fetch txs for {}", status.getAddress().toString());

                Request request = new Request.Builder().url(
                        getBlockChainTxsUrl(status.getAddress().toString())).build();
                getHttpClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Request request, IOException e) {
                        log.info("Failed to communicate with server:  " + request.toString());

                    }

                    @Override
                    public void onResponse(Response response) throws IOException {
                        try {
                            if (!response.isSuccessful()) {
                                log.info("Unable to fetch txs.");
                                log.info("[Error code] = " + response.code());
                            }
                            JSONObject reply = parseReply(response);
                            JSONArray txs = reply.getJSONArray("transactions");

                            ImmutableList.Builder<ServerClient.HistoryTx> historyTxs = ImmutableList.builder();

                            for (int j = 0; j < txs.length(); j++) {
                                JSONObject tx = txs.getJSONObject(j);
                                JSONObject histTx = new JSONObject();
                                histTx.put("tx_hash", tx.getString("fullHash"));
                                histTx.put("height", tx.getInt("height"));
                                historyTxs.add(new ServerClient.HistoryTx(histTx));
                                log.info("added to historyTx: {}", tx.getString("fullHash"));
                            }
                            listener.onTransactionHistory(status, historyTxs.build());

                        } catch (IOException e) {
                            log.info("IOException: " + e.getMessage());
                        } catch (JSONException e) {
                            log.info("Could not parse JSON: " + e.getMessage());
                        }
                    }
                });
            }
        }, 0, 15, TimeUnit.SECONDS);
    }

    @Override
    public void getTransaction(final Sha256Hash txHash,
                               final TransactionEventListener<NxtTransaction> listener) {
        Request request = new Request.Builder().url(getTransactionUrl(txHash.toString())).build();
        getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                log.info("Failed to communicate with server:  " + request.toString());
                // FIXME infinite recursion
//                getTransaction(txHash, listener);
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        log.info("Unable to fetch txs.");
                        log.info("[Error code] = " + response.code());
                    }
                    JSONObject reply = parseReply(response);

                    //String txId = reply.getString("transaction");
                    //Integer confirmations = reply.getInt("confirmations");
                    //getTransactionBytes(txId, listener, confirmations);

                    NxtTransaction tx = new NxtTransaction(type,
                            TransactionImpl.parseTransaction(reply));
                    //log.info("Transaction fetched");
                    listener.onTransactionUpdate(tx);
                } catch (IOException e) {
                    log.info("IOException: " + e.getMessage());
                } catch (JSONException e) {
                    log.info("Could not parse JSON: " + e.getMessage());
                }catch (NxtException.NotValidException e) {
                    log.info("Not valid transaction: " + e.getMessage());
                }
            }
        });

    }

    public void getTransactionBytes(final String txId,
                                    final TransactionEventListener<NxtTransaction> listener,
                                    final Integer confirmations) {
        Request request = new Request.Builder().url(getTransactionBytesUrl(txId)).build();
        getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                log.info("Failed to communicate with server:  " + request.toString());
                getTransactionBytes(txId, listener, confirmations);
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        log.info("Unable to fetch txs.");
                        log.info("[Error code] = " + response.code());
                    }
                    JSONObject reply = parseReply(response);

                    String txBytes = reply.getString("transactionBytes");
                    TransactionImpl rawTx = TransactionImpl.parseTransaction(Convert.parseHexString(txBytes));
                    rawTx.setConfirmations(confirmations);
                    NxtTransaction tx = new NxtTransaction(type, rawTx);
                    log.info("Fetching tx bytes");
                    listener.onTransactionUpdate(tx);
                } catch (IOException e) {
                    log.info("IOException: " + e.getMessage());
                } catch (JSONException e) {
                    log.info("Could not parse JSON: " + e.getMessage());
                } catch (NxtException.ValidationException e) {
                    log.info("Transaction is invalid");
                    e.printStackTrace();
                }
            }
        });

    }

    @Override
    public void broadcastTx(NxtTransaction tx, TransactionEventListener listener) {

    }

    @Override
    public boolean broadcastTxSync(final NxtTransaction tx) {
        RequestBody formBody = new FormEncodingBuilder().add("requestType","broadcastTransaction")
            .add("transactionBytes", Convert.toHexString(tx.getRawTransaction().getBytes())).build();
        Request request = new Request.Builder().url(getBaseUrl()).post(formBody).build();

        // FIXME this is not a sync call
        getHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                log.info("Failed to communicate with server:  " + request.toString());
            }

            @Override
            public void onResponse(Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        log.info("Unable to fetch txs.");
                        log.info("[Error code] = " + response.code());
                    }
                    JSONObject reply = parseReply(response);

                    log.info("Transaction broadcasted {0}", reply.toString());



                } catch (IOException e) {
                    log.info("IOException: " + e.getMessage());
                } catch (JSONException e) {
                    log.info("Could not parse JSON: " + e.getMessage());
                }
            }
        });


        return true;
    }

    @Override
    public void ping(String versionString) {

    }

    @Override
    public void addEventListener(ConnectionEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);

    }

    private void addEventListener(ConnectionEventListener listener, Executor executor) {
        boolean isNew = !ListenerRegistration.removeFromList(listener, eventListeners);
        eventListeners.add(new ListenerRegistration<ConnectionEventListener>(listener, executor));
        if (isNew && isActivelyConnected()) {
            broadcastOnConnection();
        }
    }

    private void broadcastOnConnection() {
        for (final ListenerRegistration<ConnectionEventListener> registration : eventListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onConnection(NxtServerClient.this);
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

    @Override
    public void resetConnection() {

    }

    @Override
    public void stopAsync() {
        if (stopped) return;
        stopped = true;
        if (isActivelyConnected()) broadcastOnDisconnect();
        eventListeners.clear();
        connectionExec.remove(reconnectTask);
        if (blockchainSubscription != null) {
            blockchainSubscription.shutdownNow();
            blockchainSubscription = null;
        }
        if (ecSubscription != null) {
            ecSubscription.shutdownNow();
            ecSubscription = null;
        }
        if (addressSubscription != null) {
            addressSubscription.shutdownNow();
            addressSubscription = null;
        }

    }

    @Override
    public boolean isActivelyConnected() {
        // TODO implement
        return true;
    }

    @Override
    public void startAsync() {
        // TODO implement
    }

    private Service.Listener serviceListener = new Service.Listener() {
        @Override
        public void running() {
            // Check if connection is up as this event is fired even if there is no connection
            if (isActivelyConnected()) {
                log.info("{} client connected to {}", type.getName(), lastServerAddress);
                broadcastOnConnection();
                retrySeconds = 0;
            }
        }

        @Override
        public void terminated(Service.State from) {
            log.info("{} client stopped", type.getName());
            broadcastOnDisconnect();
            failedAddresses.add(lastServerAddress);
            lastServerAddress = null;
            // Try to restart
            if (!stopped) {
                log.info("Reconnecting {} in {} seconds", type.getName(), retrySeconds);
                connectionExec.remove(reconnectTask);
                if (retrySeconds > 0) {
                    connectionExec.schedule(reconnectTask, retrySeconds, TimeUnit.SECONDS);
                } else {
                    connectionExec.execute(reconnectTask);
                }
            }
        }
    };

    public void setCacheDir(File cacheDir, int cacheSize) {
        // TODO
    }
}
