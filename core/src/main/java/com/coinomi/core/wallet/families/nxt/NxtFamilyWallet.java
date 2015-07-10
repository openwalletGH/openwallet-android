package com.coinomi.core.wallet.families.nxt;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.ValueType;
import com.coinomi.core.coins.nxt.Account;
import com.coinomi.core.coins.nxt.Convert;
import com.coinomi.core.coins.nxt.Crypto;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.BlockHeader;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.core.wallet.WalletAccountEventListener;
import com.coinomi.core.wallet.WalletPocketConnectivity;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.RedeemData;
import org.bitcoinj.wallet.WalletTransaction;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import static com.coinomi.core.CoreUtils.bytesToMnemonic;
import static com.coinomi.core.CoreUtils.bytesToMnemonicString;
import static com.coinomi.core.CoreUtils.getMnemonicToString;

/**
 * @author John L. Jegutanis
 */
public class NxtFamilyWallet implements WalletAccount {
    NxtFamilyKey rootKey;
    private final CoinType type;
    private final KeyCrypter crypter;
    private final KeyParameter encKey;
    private final NxtFamilyAddress address;
    // Wallet that this account belongs
    @Nullable private transient Wallet wallet = null;

    public NxtFamilyWallet(DeterministicKey key, CoinType type, KeyCrypter crypter, KeyParameter encKey) {
        String secret = getMnemonicToString(bytesToMnemonic(key.getPrivKeyBytes()));
        rootKey = new NxtFamilyKey(key);
        address = new NxtFamilyAddress(type, key);
        this.type = type;
        this.crypter = crypter;
        this.encKey = encKey;
    }

    @Override
    public String getId() {
        return Convert.toUnsignedLong(address.getAccountId());
    }

    @Override
    public String getDescription() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setDescription(String description) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public byte[] getPublicKey() {
        return rootKey.getPublicKeyBytes();
    }

    @Override
    public byte[] getPrivateKeyBytes() {
        return rootKey.getPrivKeyBytes();
    }

    @Override
    public String getPublicKeyMnemonic() {
        return address.getRsAccount();
    }

    @Override
    public String getPrivateKeyMnemonic() {
        return rootKey.getMnemonic();
    }


    @Override
    public CoinType getCoinType() {
        return type;
    }

    @Override
    public boolean isNew() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Value getBalance() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void refresh() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isConnected() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public WalletPocketConnectivity getConnectivityStatus() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public AbstractAddress getChangeAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public AbstractAddress getReceiveAddress() {
        return address;
    }

    @Override
    public AbstractAddress getRefundAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Address getChangeBitAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Address getReceiveBitAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Address getRefundBitAddress() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Transaction getTransaction(String transactionId) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, Transaction> getUnspentTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, Transaction> getPendingTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactions() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<Address> getActiveAddresses() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void markAddressAsUsed(Address address) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    @Override
    public Wallet getWallet() {
        return wallet;
    }

    @Override
    public void walletSaveLater() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void walletSaveNow() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isEncryptable() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isEncrypted() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public KeyCrypter getKeyCrypter() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void decrypt(KeyParameter aesKey) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean equals(WalletAccount otherAccount) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void addEventListener(WalletAccountEventListener listener) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void addEventListener(WalletAccountEventListener listener, Executor executor) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean removeEventListener(WalletAccountEventListener listener) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isType(WalletAccount other) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isType(ValueType type) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isType(Address address) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isAddressMine(Address address) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isLoading() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void maybeInitializeAllKeys() { /* Doesn't need initialization */ }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onDisconnect() {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        throw new RuntimeException("Not implemented");
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isWatchedScript(Script script) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isPayToScriptHashMine(byte[] payToScriptHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactionPool(WalletTransaction.Pool pool) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onNewBlock(BlockHeader header) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onAddressStatusUpdate(AddressStatus status) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionHistory(AddressStatus status, List<ServerClient.HistoryTx> historyTxes) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionUpdate(Transaction tx) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionBroadcast(Transaction transaction) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void onTransactionBroadcastError(Transaction tx) {
        throw new RuntimeException("Not implemented");
    }
}
