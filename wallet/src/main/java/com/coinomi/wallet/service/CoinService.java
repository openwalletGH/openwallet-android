package com.coinomi.wallet.service;

import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.StoredBlock;

import java.util.List;

import javax.annotation.CheckForNull;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
public interface CoinService {
    public static final String ACTION_CANCEL_COINS_RECEIVED = CoinService.class.getPackage().getName() + ".cancel_coins_received";
    public static final String ACTION_RESET_WALLET = CoinService.class.getPackage().getName() + ".reset_wallet";
    public static final String ACTION_BROADCAST_TRANSACTION = CoinService.class.getPackage().getName() + ".broadcast_transaction";
    public static final String ACTION_BROADCAST_TRANSACTION_HASH = "hash";

    enum ServiceMode {
        NORMAL,
        CANCEL_COINS_RECEIVED,
        RESET_WALLET
    }
}
