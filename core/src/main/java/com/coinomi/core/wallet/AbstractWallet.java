package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.utils.Threading;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author John L. Jegutanis
 */
public abstract class AbstractWallet implements WalletAccount { //extends TransactionWatcherWallet {
    protected final String id;
    protected String description;
    protected final CoinType type;
    protected final ReentrantLock lock = Threading.lock("AbstractWallet");

    public AbstractWallet(CoinType coinType, String id) {
        this.type = coinType;
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public CoinType getCoinType() {
        return type;
    }

    /**
     * Set the description of the wallet.
     * This is a Unicode encoding string typically entered by the user as descriptive text for the wallet.
     */
    @Override
    public void setDescription(String description) {
        lock.lock();
        this.description = description;
        lock.unlock();
        walletSaveNow();
    }

    /**
     * Get the description of the wallet. See {@link WalletPocketHD#setDescription(String))}
     */
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void completeAndSignTx(SendRequest request) throws WalletAccountException {
        if (request.isCompleted()) {
            signTransaction(request);
        } else {
            completeTransaction(request);
        }
    }
}
