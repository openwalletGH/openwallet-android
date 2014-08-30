package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.interfaces.ConnectionEventListener;
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.crypto.MnemonicCode;
import com.google.bitcoin.crypto.MnemonicException;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.utils.Threading;
import com.google.bitcoin.wallet.DeterministicSeed;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 */
final public class Wallet implements ConnectionEventListener {
    private static final Logger log = LoggerFactory.getLogger(Wallet.class);

    private final ReentrantLock lock = Threading.lock("KeyChain");

    @GuardedBy("lock") private final LinkedHashMap<CoinType, WalletPocket> pockets;

    private final DeterministicKey masterKey;

    private int lastBlockSeenHeight;


    private final static int ACCOUNT_ZERO = 0;

    @Nullable transient BlockchainConnection blockchainConnection;
    private int version;

    public Wallet(List<String> mnemonic) throws IOException, MnemonicException {
        this(mnemonic, "");
    }

    public Wallet(List<String> mnemonic, String password) throws IOException, MnemonicException {
        new MnemonicCode().check(mnemonic);
        DeterministicSeed seed = new DeterministicSeed(mnemonic, "", 0);

        masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
        pockets = new LinkedHashMap<CoinType, WalletPocket>();
    }

    public Wallet(DeterministicKey masterKey) {
        this.masterKey = masterKey;
        pockets = new LinkedHashMap<CoinType, WalletPocket>();
    }

    public static List<String> generateMnemonic() throws IOException {
        byte[] entropy = new byte[16];

        SecureRandom sr = new SecureRandom();
        sr.nextBytes(entropy);

        MnemonicCode mc = new MnemonicCode();
        List<String> mnemonic;
        try {
            mnemonic = mc.toMnemonic(entropy);
        } catch (MnemonicException.MnemonicLengthException e) {
            throw new RuntimeException(e); // should not happen, we have 16bytes of entropy
        }

        return mnemonic;
    }

    public void createCoinPockets(List<CoinType> coins) {
        for (CoinType coin : coins) {
            log.info("Creating coin pocket for {}", coin);
            getPocket(coin);
        }
    }

    public WalletPocket getPocket(CoinType coinType) {
        lock.lock();
        try {
            if (!pockets.containsKey(coinType)) {
                createPocket(coinType);
            }
            return pockets.get(coinType);
        } finally {
            lock.unlock();
        }
    }

    public List<WalletPocket> getPockets() {
        lock.lock();
        try {
            return ImmutableList.copyOf(pockets.values());
        } finally {
            lock.unlock();
        }
    }

    private void createPocket(CoinType coinType) {
        DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
        DeterministicKey rootKey = hierarchy.get(coinType.getBip44Path(ACCOUNT_ZERO), false, true);
        pockets.put(coinType, new WalletPocket(rootKey, coinType, masterKey.getKeyCrypter()));
    }


    public void addPocket(WalletPocket pocket) {
        checkState(!pockets.containsKey(pocket.getCoinType()), "Cannot replace an existing wallet pocket");
        //TODO check if key crypter is the same
        pockets.put(pocket.getCoinType(), pocket);
    }

    public DeterministicKey getMasterKey() {
        lock.lock();
        try {
            return masterKey;
        } finally {
            lock.unlock();
        }
    }

    List<CoinType> getCoinTypes() {
        lock.lock();
        try {
            return ImmutableList.copyOf(pockets.keySet());
        } finally {
            lock.unlock();
        }
    }

    /** Returns the {@link KeyCrypter} in use or null if the key chain is not encrypted. */
    @Nullable
    public KeyCrypter getKeyCrypter() {
        lock.lock();
        try {
            return masterKey.getKeyCrypter();
        } finally {
            lock.unlock();
        }
    }

    public int getLastBlockSeenHeight() {
        //TODO
        return lastBlockSeenHeight;
    }

    public Transaction getTransaction(Sha256Hash hash) {
        //TODO
        return null;
    }

    public void saveToFile(File walletFile) {
        //TODO
    }

    @Override
    public void onConnection(BlockchainConnection blockchainConnection) {
        this.blockchainConnection = blockchainConnection;
        for (CoinType coin : getCoinTypes()) {
            WalletPocket pocket = getPocket(coin);
            if (blockchainConnection != null) {
                pocket.onConnection(blockchainConnection);
            }
        }
    }

    @Override
    public void onDisconnect() {
        this.blockchainConnection = null;
    }


    public void sendCoins(Address address, Coin amount) throws InsufficientMoneyException {
        WalletPocket pocket = getPocket((CoinType) address.getParameters());

        pocket.sendCoins(address, amount);
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getVersion() {
        return version;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    Protos.Wallet toProtobuf() {
        lock.lock();
        try {
            return WalletProtobufSerializer.toProtobuf(this);
        } finally {
            lock.unlock();
        }
    }
    /**
     * Returns a wallet deserialized from the given file.
     */
    public static Wallet loadFromFile(File f) throws UnreadableWalletException {
        try {
            FileInputStream stream = null;
            try {
                stream = new FileInputStream(f);
                return loadFromFileStream(stream);
            } finally {
                if (stream != null) stream.close();
            }
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not open file", e);
        }
    }

    /**
     * Returns a wallet deserialized from the given input stream.
     */
    public static Wallet loadFromFileStream(InputStream stream) throws UnreadableWalletException {
        return WalletProtobufSerializer.readWallet(stream);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Encrypt the keys in the group using the KeyCrypter and the AES key. A good default KeyCrypter to use is
     * {@link com.google.bitcoin.crypto.KeyCrypterScrypt}.
     *
     * @throws com.google.bitcoin.crypto.KeyCrypterException Thrown if the wallet encryption fails for some reason,
     *         leaving the group unchanged.
     */
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        checkNotNull(keyCrypter);
        checkNotNull(aesKey);

        lock.lock();
        try {
            for (WalletPocket pocket : pockets.values()) {
                pocket.encrypt(keyCrypter, aesKey);
            }
        } finally {
            lock.unlock();
        }
    }

    // Full decryption is not needed as the keys can be decrypted on the fly when signing transactions
}
