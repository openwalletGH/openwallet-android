package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;

/**
 * @author John L. Jegutanis
 */
public abstract class AbstractWallet extends TransactionWatcherWallet {
    protected final String id;
    private String description;

    public AbstractWallet(CoinType coinType, String id) {
        super(coinType);
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
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
}
