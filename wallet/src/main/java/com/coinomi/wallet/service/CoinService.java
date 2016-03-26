package com.coinomi.wallet.service;

import org.bitcoinj.core.Peer;
import org.bitcoinj.core.StoredBlock;

import java.util.List;

import javax.annotation.CheckForNull;

/**
 * @author John L. Jegutanis
 * @author Andreas Schildbach
 */
public interface CoinService {
    String ACTION_CANCEL_COINS_RECEIVED = CoinService.class.getPackage().getName() + ".cancel_coins_received";
    String ACTION_CONNECT_COIN = CoinService.class.getPackage().getName() + ".connect_coin";
    String ACTION_CONNECT_ALL_COIN = CoinService.class.getPackage().getName() + ".connect_all_coin";
    String ACTION_RESET_ACCOUNT = CoinService.class.getPackage().getName() + ".reset_account";
    String ACTION_RESET_WALLET = CoinService.class.getPackage().getName() + ".reset_wallet";
    String ACTION_CLEAR_CONNECTIONS = CoinService.class.getPackage().getName() + ".clear_connections";
    String ACTION_BROADCAST_TRANSACTION = CoinService.class.getPackage().getName() + ".broadcast_transaction";
    String ACTION_BROADCAST_TRANSACTION_HASH = "hash";

    enum ServiceMode {
        NORMAL,
        CANCEL_COINS_RECEIVED
    }
}
