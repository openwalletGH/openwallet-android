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

    public static final String ACTION_BLOCKCHAIN_STATE = CoinService.class.getPackage().getName() + ".blockchain_state";
    public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE = "best_chain_date";
    public static final String ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_HEIGHT = "best_chain_height";
    public static final String ACTION_BLOCKCHAIN_STATE_REPLAYING = "replaying";
    public static final String ACTION_BLOCKCHAIN_STATE_DOWNLOAD = "download";
    public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK = 0;
    public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM = 1;
    public static final int ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM = 2;

    public static final String ACTION_CANCEL_COINS_RECEIVED = CoinService.class.getPackage().getName() + ".cancel_coins_received";
    public static final String ACTION_RESET_BLOCKCHAIN = CoinService.class.getPackage().getName() + ".reset_blockchain";
    public static final String ACTION_BROADCAST_TRANSACTION = CoinService.class.getPackage().getName() + ".broadcast_transaction";
    public static final String ACTION_BROADCAST_TRANSACTION_HASH = "hash";

//    @CheckForNull
//    List<Peer> getConnectedPeers();

    List<StoredBlock> getRecentBlocks(int maxBlocks);
}
