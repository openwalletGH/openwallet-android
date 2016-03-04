/**
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.coinomi.core.wallet;


import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.families.bitcoin.CoinSelector;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet.MissingSigsMode;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.Serializable;

/**
 * A SendRequest gives the wallet information about precisely how to send money to a recipient or set of recipients.
 * Static methods are provided to help you create SendRequests and there are a few helper methods on the wallet that
 * just simplify the most common use cases. You may wish to customize a SendRequest if you want to attach a fee or
 * modify the change address.
 */
public class SendRequest<T extends AbstractTransaction> implements Serializable {

    /**
     * The blockchain network (Bitcoin, Dogecoin,..) that this request is going to transact
     */
    public CoinType type;

    /**
     * <p>A transaction, probably incomplete, that describes the outline of what you want to do. This typically will
     * mean it has some outputs to the intended destinations, but no inputs or change address (and therefore no
     * fees) - the wallet will calculate all that for you and update tx later.</p>
     */
    public T tx;

    /**
     * When emptyWallet is set, all coins selected by the coin selector are sent to the first output in tx
     * (its value is ignored and set to {@link org.bitcoinj.core.Wallet#getBalance()} - the fees required
     * for the transaction). Any additional outputs are removed.
     */
    public boolean emptyWallet = false;

    /**
     * "Change" means the difference between the value gathered by a transactions inputs (the size of which you
     * don't really control as it depends on who sent you money), and the value being sent somewhere else. The
     * change address should be selected from this wallet, normally. <b>If null this will be chosen for you.</b>
     */
    public AbstractAddress changeAddress = null;

    /**
     * <p>A transaction can have a fee attached, which is defined as the difference between the input values
     * and output values. Any value taken in that is not provided to an output can be claimed by a miner. This
     * is how mining is incentivized in later years of the Bitcoin system when inflation drops. It also provides
     * a way for people to prioritize their transactions over others and is used as a way to make denial of service
     * attacks expensive.</p>
     *
     * <p>This is a constant fee (in satoshis) which will be added to the transaction. It is recommended that it be
     * at least {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE} if it is set, as default reference clients will
     * otherwise simply treat the transaction as if there were no fee at all.</p>
     *
     * <p>You might also consider adding a {@link SendRequest#feePerTxSize} to set the fee per kb of transaction size
     * (rounded down to the nearest kb) as that is how transactions are sorted when added to a block by miners.</p>
     */
    public Value fee;

    /**
     * <p>A transaction can have a fee attached, which is defined as the difference between the input values
     * and output values. Any value taken in that is not provided to an output can be claimed by a miner. This
     * is how mining is incentivized in later years of the Bitcoin system when inflation drops. It also provides
     * a way for people to prioritize their transactions over others and is used as a way to make denial of service
     * attacks expensive.</p>
     *
     * <p>This is a dynamic fee (in satoshis) which will be added to the transaction for each kilobyte in size
     * including the first. This is useful as as miners usually sort pending transactions by their fee per unit size
     * when choosing which transactions to add to a block. Note that, to keep this equivalent to the reference
     * client definition, a kilobyte is defined as 1000 bytes, not 1024.</p>
     *
     * <p>You might also consider using a {@link SendRequest#fee} to set the fee added for the first kb of size.</p>
     */
    public Value feePerTxSize;

    /**
     * <p>Requires that there be enough fee for a default reference client to at least relay the transaction.
     * (ie ensure the transaction will not be outright rejected by the network). Defaults to true, you should
     * only set this to false if you know what you're doing.</p>
     *
     * <p>Note that this does not enforce certain fee rules that only apply to transactions which are larger than
     * 26,000 bytes. If you get a transaction which is that large, you should set a fee and feeValue of at least
     * {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE}.</p>
     */
    public boolean ensureMinRequiredFee = true;

    /**
     * If true (the default), the inputs will be signed.
     */
    public boolean signTransaction = true;

    /**
     * If true, the wallet will use unconfirmed received coins (that could be double spent)
     */
    public boolean useUnsafeOutputs = false;

    /**
     * If true, the wallet will use mined funds coins are not sufficiently confirmed
     */
    public boolean useImmatureCoinbases = false;

    /**
     * The AES key to use to decrypt the private keys before signing.
     * If null then no decryption will be performed and if decryption is required an exception will be thrown.
     * You can get this from a password by doing wallet.getKeyCrypter().deriveKey(password).
     */
    transient public KeyParameter aesKey = null;

    /**
     * If not null, the {@link org.bitcoinj.wallet.CoinSelector} to use instead of the wallets default. Coin selectors are
     * responsible for choosing which transaction outputs (coins) in a wallet to use given the desired send value
     * amount.
     */
    transient public CoinSelector coinSelector = null;

    /**
     * If true (the default), the outputs will be shuffled during completion to randomize the location of the change
     * output, if any. This is normally what you want for privacy reasons but in unit tests it can be annoying
     * so it can be disabled here.
     */
    public boolean shuffleOutputs = true;

    /**
     * Specifies what to do with missing signatures left after completing this request. Default strategy is to
     * throw an exception on missing signature ({@link MissingSigsMode#THROW}).
     * @see MissingSigsMode
     */
    transient public MissingSigsMode missingSigsMode = MissingSigsMode.THROW;

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    /**
     * Attaches a message to the transaction. There is no guarantee that the coin supports messages
     * or that the recipient will ultimately get them or if the message will be recorded to on the
     * blockchain i.e. Bitcoin, Litecoin messages could be stored on a public server
     */
    public TxMessage txMessage;

    // Tracks if this has been passed to wallet.completeTransaction already: just a safety check.
    private boolean completed;

    protected SendRequest(CoinType type) {
        this.type = type;
        switch (type.getFeePolicy()) {
            case FLAT_FEE:
                feePerTxSize = type.value(0);
                fee = type.getFeeValue();
                break;
            case FEE_PER_KB:
                feePerTxSize = type.getFeeValue();
                fee = type.value(0);
                break;
            default:
                throw new RuntimeException("Unknown fee policy: " + type.getFeePolicy());
        }
    }

    @Override
    public String toString() {
        // print only the user-settable fields
        ToStringHelper helper = Objects.toStringHelper(this).omitNullValues();
        helper.add("emptyWallet", emptyWallet);
        helper.add("changeAddress", changeAddress);
        helper.add("fee", fee);
        helper.add("feePerTxSize", feePerTxSize);
        helper.add("ensureMinRequiredFee", ensureMinRequiredFee);
        helper.add("signTransaction", signTransaction);
        helper.add("aesKey", aesKey != null ? "set" : null); // careful to not leak the key
        helper.add("coinSelector", coinSelector);
        helper.add("shuffleOutputs", shuffleOutputs);
        return helper.toString();
    }
}