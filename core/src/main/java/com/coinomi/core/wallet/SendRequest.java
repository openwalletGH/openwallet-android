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


import com.coinomi.core.coins.BurstMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.NxtMain;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.coins.nxt.Appendix;
import com.coinomi.core.coins.nxt.Attachment;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.TransactionImpl;
import com.coinomi.core.messages.TxMessage;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;
import com.coinomi.core.wallet.families.nxt.NxtFamilyAddress;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet.MissingSigsMode;
import org.bitcoinj.wallet.CoinSelector;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.Serializable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A SendRequest gives the wallet information about precisely how to send money to a recipient or set of recipients.
 * Static methods are provided to help you create SendRequests and there are a few helper methods on the wallet that
 * just simplify the most common use cases. You may wish to customize a SendRequest if you want to attach a fee or
 * modify the change address.
 */
public class SendRequest implements Serializable{

    /**
     * The blockchain network (Bitcoin, Dogecoin,..) that this request is going to transact
     */
    public CoinType type;

    /**
     * <p>A transaction, probably incomplete, that describes the outline of what you want to do. This typically will
     * mean it has some outputs to the intended destinations, but no inputs or change address (and therefore no
     * fees) - the wallet will calculate all that for you and update tx later.</p>
     *
     * <p>Be careful when adding outputs that you check the min output value
     * ({@link org.bitcoinj.core.TransactionOutput#getMinNonDustValue(Coin)}) to avoid the whole transaction being rejected
     * because one output is dust.</p>
     *
     * <p>If there are already inputs to the transaction, make sure their out point has a connected output,
     * otherwise their value will be added to fee.  Also ensure they are either signed or are spendable by a wallet
     * key, otherwise the behavior of {@link WalletPocketHD#completeTransaction(SendRequest)} is undefined (likely
     * RuntimeException).</p>
     */
    // TODO unify and make abstract
    public Transaction tx;
    public com.coinomi.core.coins.nxt.Transaction nxtTx;
    public com.coinomi.core.coins.nxt.TransactionImpl.BuilderImpl nxtTxBuilder;

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
     * <p>You might also consider adding a {@link SendRequest#feePerKb} to set the fee per kb of transaction size
     * (rounded down to the nearest kb) as that is how transactions are sorted when added to a block by miners.</p>
     */
    // TODO change to Value
    public Coin fee;

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
    // TODO change to Value
    public Coin feePerKb;

    /**
     * <p>Requires that there be enough fee for a default reference client to at least relay the transaction.
     * (ie ensure the transaction will not be outright rejected by the network). Defaults to true, you should
     * only set this to false if you know what you're doing.</p>
     *
     * <p>Note that this does not enforce certain fee rules that only apply to transactions which are larger than
     * 26,000 bytes. If you get a transaction which is that large, you should set a fee and feePerKb of at least
     * {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE}.</p>
     */
    public boolean ensureMinRequiredFee = true;

    /**
     * If true (the default), the inputs will be signed.
     */
    public boolean signInputs = true;

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

    private SendRequest() {}

    /**
     * <p>Creates a new SendRequest to the given address for the given value.</p>
     *
     * <p>Be very careful when value is smaller than {@link Transaction#MIN_NONDUST_OUTPUT} as the transaction will
     * likely be rejected by the network in this case.</p>
     */

    // TODO combine the following function methods -> SendRequest to(AbstractAddress destination, Value amount) {
    public static SendRequest to(AbstractAddress destination, Coin amount) {
        SendRequest req = new SendRequest();
        req.type = destination.getType();
        checkNotNull(req.type, "Address is for an unknown network");
        switch (req.type.getFamilyEnum()) {
            case NXT:
                throw new RuntimeException("Not implemented");

            case BITCOIN:
            case NUBITS:
            case PEERCOIN:
            case REDDCOIN:
            case VPNCOIN:
                break;
            default:
            case FIAT:
                throw new RuntimeException("Unsupported family: " + req.type.getFamily());
        }


        req.feePerKb = req.type.feePerKb().toCoin();
        req.fee = req.type.value(0).toCoin();
        req.tx = new Transaction(req.type);
        req.tx.addOutput(amount, (BitAddress) destination);
        return req;
    }

    public static SendRequest to(WalletAccount from, NxtFamilyAddress destination, Value amount) {
        SendRequest req = new SendRequest();
        req.type = destination.getType();
        switch (req.type.getFeePolicy()) {
            case FLAT_FEE:
                req.feePerKb = req.type.value(0).toCoin();
                req.fee = req.type.feePerKb().toCoin();
                break;
            case FEE_PER_KB:
                req.feePerKb = req.type.feePerKb().toCoin();
                req.fee = req.type.value(0).toCoin();
                break;
            default:
                throw new RuntimeException("Unknown fee policy: " + req.type.getFeePolicy());
        }

        byte version = (byte) req.type.getTransactionVersion();
        int timestamp;
        if (req.type.equals(NxtMain.get())) {
            timestamp = Convert.toNxtEpochTime(System.currentTimeMillis());
        } else if (req.type.equals(BurstMain.get())){
            timestamp = Convert.toBurstEpochTime(System.currentTimeMillis());
        } else {
            throw new RuntimeException("Unexpected NXT family type: " + req.type.toString());
        }

        TransactionImpl.BuilderImpl builder = new TransactionImpl.BuilderImpl(version,
                from.getPublicKey(), amount.value, req.fee.value, timestamp,
                NxtFamily.DEFAULT_DEADLINE, Attachment.ORDINARY_PAYMENT);

        builder.recipientId(destination.getAccountId());

        // TODO extra check, query the server if the public key announcement is actually needed
        if (destination.getPublicKey() != null) {
            Appendix.PublicKeyAnnouncement publicKeyAnnouncement
                    = new Appendix.PublicKeyAnnouncement(destination.getPublicKey());
            builder.publicKeyAnnouncement(publicKeyAnnouncement);
        }

        req.nxtTxBuilder = builder;

        return req;
    }

    // TODO implement a universal method for Bitcoin and NXT that uses AbstractAddress destination
    public static SendRequest emptyWallet(AbstractAddress destination) {
        SendRequest req = new SendRequest();
        req.type = destination.getType();
        checkNotNull(req.type, "Address is for an unknown network");
        switch (req.type.getFamilyEnum()) {
            case NXT:
                throw new RuntimeException("Not implemented");

            case BITCOIN:
            case NUBITS:
            case PEERCOIN:
            case REDDCOIN:
            case VPNCOIN:
                break;
            default:
            case FIAT:
                throw new RuntimeException("Unsupported family: " + req.type.getFamily());
        }

        req.feePerKb = req.type.getFeePerKb();
        req.tx = new Transaction(req.type);
        req.tx.addOutput(Coin.ZERO, (BitAddress) destination);
        req.emptyWallet = true;
        return req;
    }

    @Override
    public String toString() {
        // print only the user-settable fields
        ToStringHelper helper = Objects.toStringHelper(this).omitNullValues();
        helper.add("emptyWallet", emptyWallet);
        helper.add("changeAddress", changeAddress);
        helper.add("fee", fee);
        helper.add("feePerKb", feePerKb);
        helper.add("ensureMinRequiredFee", ensureMinRequiredFee);
        helper.add("signInputs", signInputs);
        helper.add("aesKey", aesKey != null ? "set" : null); // careful to not leak the key
        helper.add("coinSelector", coinSelector);
        helper.add("shuffleOutputs", shuffleOutputs);
        return helper.toString();
    }
}