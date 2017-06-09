package com.openwallet.core.wallet;

import com.openwallet.core.wallet.AbstractTransaction;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
public abstract class WalletTransaction<T extends AbstractTransaction> {
    public enum Pool {
        CONFIRMED, // in best chain
        PENDING, // a pending tx we would like to go into the best chain
    }

    private final T transaction;
    private final Pool pool;

    public WalletTransaction(Pool pool, T transaction) {
        this.pool = checkNotNull(pool);
        this.transaction = transaction;
    }

    public T getTransaction() {
        return transaction;
    }

    public Pool getPool() {
        return pool;
    }
}
