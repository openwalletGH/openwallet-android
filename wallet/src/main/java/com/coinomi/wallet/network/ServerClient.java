package com.coinomi.wallet.network;

import com.coinomi.stratumj.ServerAddress;
import com.coinomi.stratumj.StratumClient;
import com.coinomi.wallet.WalletImpl;
import com.coinomi.wallet.coins.Coin;
import com.google.common.collect.HashBiMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
            public void stopped() { }

            public void healthy() {
                log.info("All coin clients are running");
            }

            public void failure(Service service) {
                StratumClient client = (StratumClient)service;

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


    public void addWallet(WalletImpl wallet) {
        this.wallet = wallet;
    }

    public void startAsync() {
        manager.startAsync();
    }

    public void stopAsync() {
        manager.stopAsync();
    }
}
