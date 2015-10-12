package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.ValueType;
import com.coinomi.core.util.TypeUtils;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.utils.Threading;

import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

/**
 * @author John L. Jegutanis
 */
public abstract class AbstractWallet<T> implements WalletAccount<T> {
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

    @Nullable
    public T getTransaction(String transactionId) {
        return null;
    }

    public AbstractTransaction getAbstractTransaction(String transactionId) {
        return null;
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

    @Override
    public boolean isType(WalletAccount other) {
        return TypeUtils.is(type, other);
    }

    @Override
    public boolean isType(ValueType otherType) {
        return TypeUtils.is(type, otherType);
    }

    @Override
    public boolean isType(AbstractAddress address) {
        return TypeUtils.is(type, address);
    }

    public WalletPocketConnectivity getConnectivityStatus() {
        if (!isConnected()) {
            return WalletPocketConnectivity.DISCONNECTED;
        } else {
            if (isLoading()) {
                // TODO support LOADING state, for now is just CONNECTED
                return WalletPocketConnectivity.CONNECTED;
            } else {
                return WalletPocketConnectivity.CONNECTED;
            }
        }
    }

    @Override
    public boolean equals(WalletAccount other) {
        return other != null &&
                getId().equals(other.getId()) &&
                getCoinType().equals(other.getCoinType());
    }
}
