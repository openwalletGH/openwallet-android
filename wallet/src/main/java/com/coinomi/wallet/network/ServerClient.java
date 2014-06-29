package com.coinomi.wallet.network;

import com.coinomi.stratumj.ServerAddress;
import com.coinomi.stratumj.StratumClient;
import com.coinomi.stratumj.messages.CallMessage;
import com.coinomi.stratumj.messages.ResultMessage;
import com.coinomi.wallet.WalletImpl;
import com.coinomi.wallet.coins.Coin;
import com.google.common.base.Optional;
import com.google.common.collect.HashBiMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.SettableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Giannis Dzegoutanis
 */
public class ServerClient {
    private static final Logger log = LoggerFactory.getLogger(ServerClient.class);

    private HashBiMap<Coin, StratumClient> connections;

    final ServiceManager manager;
    private WalletImpl wallet;

    public ServerClient(List<CoinAddress> coins) {
        connections = HashBiMap.create(coins.size());

        for (CoinAddress coinAddress : coins) {
            List<ServerAddress> addresses = coinAddress.getAddresses();
            StratumClient client = new StratumClient(addresses);
            connections.put(coinAddress.getCoin(), client);
        }

        manager = new ServiceManager(connections.values());
        ServiceManager.Listener managerListener = new ServiceManager.Listener() {
            public void stopped() {
            }

            public void healthy() {
                log.info("All coin clients are running");
                syncWallet();
            }

            public void failure(Service service) {
                StratumClient client = (StratumClient) service;

                log.error("Client failed: " + connections.inverse().get(client));
            }
        };
        manager.addListener(managerListener, MoreExecutors.sameThreadExecutor());

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

    private void syncWallet() {
//        for (Coin c : connections.keySet()) {
//            wallet.
//            getUnspentTx(c, )
//        }
    }


    public void addWallet(WalletImpl wallet) {
        this.wallet = wallet;
    }

    public void startAsync() {
        manager.startAsync();
    }

    public void stopAsync() {
        manager.stopAsync();
    }

    public ListenableFuture<Transaction[]> getUnspentTx(Coin coin, String address) throws Exception {
        StratumClient client = connections.get(coin);
        assert client != null;

        final SettableFuture<Transaction[]> future = SettableFuture.create();

        final ListenableFuture<ResultMessage> call = client.call(new CallMessage("blockchain.address.listunspent",
                Optional.of(Arrays.asList((Object) address))));
        call.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONArray result = call.get().getResult();
                    Transaction[] txs = new Transaction[result.length()];
                    for (int i = 0; i < txs.length; i++)
                        txs[i] = new Transaction(result.getJSONObject(i));

                    future.set(txs);
                } catch (Exception ex) {
                    future.setException(ex);
                }
            }
        }, MoreExecutors.sameThreadExecutor());

        return future;
    }

    public class Transaction {
        private String txHash;
        private int txPos;
        private long value;
        private int height;

        public Transaction(JSONObject json) throws JSONException {
            txHash = json.getString("tx_hash");
            txPos = json.getInt("tx_pos");
            value = json.getLong("value");
            height = json.getInt("height");
        }

        public String getTxHash() {
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
    }
}
