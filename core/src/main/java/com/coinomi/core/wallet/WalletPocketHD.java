/**
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2014 John L. Jegutanis
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
import com.coinomi.core.protos.Protos;
import com.coinomi.core.wallet.exceptions.Bip44KeyLookAheadExceededException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.RedeemData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkArgument;
import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;
import static org.bitcoinj.wallet.KeyChain.KeyPurpose.CHANGE;
import static org.bitcoinj.wallet.KeyChain.KeyPurpose.RECEIVE_FUNDS;
import static org.bitcoinj.wallet.KeyChain.KeyPurpose.REFUND;

/**
 * @author John L. Jegutanis
 *
 *
 */
public class WalletPocketHD extends AbstractWallet {
    private static final Logger log = LoggerFactory.getLogger(WalletPocketHD.class);

    private final TransactionCreator transactionCreator;

    @VisibleForTesting SimpleHDKeyChain keys;

    public WalletPocketHD(DeterministicKey rootKey, CoinType coinType,
                          @Nullable KeyCrypter keyCrypter, @Nullable KeyParameter key) {
        this(new SimpleHDKeyChain(rootKey, keyCrypter, key), coinType);
    }

    WalletPocketHD(SimpleHDKeyChain keys, CoinType coinType) {
        this(keys.getId(coinType.getId()), keys, coinType);
    }

    WalletPocketHD(String id, SimpleHDKeyChain keys, CoinType coinType) {
        super(checkNotNull(coinType), id);
        this.keys = checkNotNull(keys);
        transactionCreator = new TransactionCreator(this);
    }

    /******************************************************************************************************************/

    //region Vending transactions and other internal state

    /**
     * Get the BIP44 index of this account
     */
    public int getAccountIndex() {
        lock.lock();
        try {
            return keys.getAccountIndex();
        } finally {
            lock.unlock();
        }
    }


    @Override
    public boolean equals(WalletAccount other) {
        return other != null &&
                getId().equals(other.getId()) &&
                getCoinType().equals(other.getCoinType());
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Serialization support
    //
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    List<Protos.Key> serializeKeychainToProtobuf() {
        lock.lock();
        try {
            return keys.toProtobuf();
        } finally {
            lock.unlock();
        }
    }

    @VisibleForTesting Protos.WalletPocket toProtobuf() {
        lock.lock();
        try {
            return WalletPocketProtobufSerializer.toProtobuf(this);
        } finally {
            lock.unlock();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Encryption support
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public boolean isEncryptable() {
        return true;
    }

    @Override
    public boolean isEncrypted() {
        lock.lock();
        try {
            return keys.isEncrypted();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the wallet pocket's KeyCrypter, or null if the wallet pocket is not encrypted.
     * (Used in encrypting/ decrypting an ECKey).
     */
    @Nullable
    @Override
    public KeyCrypter getKeyCrypter() {
        lock.lock();
        try {
            return keys.getKeyCrypter();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Encrypt the keys in the group using the KeyCrypter and the AES key. A good default KeyCrypter to use is
     * {@link org.bitcoinj.crypto.KeyCrypterScrypt}.
     *
     * @throws org.bitcoinj.crypto.KeyCrypterException Thrown if the wallet encryption fails for some reason,
     *         leaving the group unchanged.
     */
    @Override
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        checkNotNull(keyCrypter);
        checkNotNull(aesKey);

        lock.lock();
        try {
            this.keys = this.keys.toEncrypted(keyCrypter, aesKey);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Decrypt the keys in the group using the previously given key crypter and the AES key. A good default
     * KeyCrypter to use is {@link org.bitcoinj.crypto.KeyCrypterScrypt}.
     *
     * @throws org.bitcoinj.crypto.KeyCrypterException Thrown if the wallet decryption fails for some reason, leaving the group unchanged.
     */
    @Override
    public void decrypt(KeyParameter aesKey) {
        checkNotNull(aesKey);

        lock.lock();
        try {
            this.keys = this.keys.toDecrypted(aesKey);
        } finally {
            lock.unlock();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Transaction signing support
    //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sends coins to the given address but does not broadcast the resulting pending transaction.
     */
    public SendRequest sendCoinsOffline(Address address, Coin amount) throws InsufficientMoneyException {
        return sendCoinsOffline(address, amount, (KeyParameter) null);
    }

    /**
     * {@link #sendCoinsOffline(Address, Coin)}
     */
    public SendRequest sendCoinsOffline(Address address, Coin amount, @Nullable String password)
            throws InsufficientMoneyException {
        KeyParameter key = null;
        if (password != null) {
            checkState(isEncrypted());
            key = checkNotNull(getKeyCrypter()).deriveKey(password);
        }
        return sendCoinsOffline(address, amount, key);
    }

    /**
     * {@link #sendCoinsOffline(Address, Coin)}
     */
    public SendRequest sendCoinsOffline(Address address, Coin amount, @Nullable KeyParameter aesKey)
            throws InsufficientMoneyException {
        checkState(address.getParameters() instanceof CoinType);
        SendRequest request = SendRequest.to(address, amount);
        request.aesKey = aesKey;

        return request;
    }

    @Override
    public boolean isAddressMine(Address address) {
        return address != null && address.getParameters().equals(coinType) &&
                (address.isP2SHAddress() ?
                        isPayToScriptHashMine(address.getHash160()) :
                        isPubKeyHashMine(address.getHash160()));
    }

    @Override
    public void signMessage(SignedMessage unsignedMessage, @Nullable KeyParameter aesKey) {
        String message = unsignedMessage.message;
        lock.lock();
        try {
            ECKey key;
            try {
                Address address = new Address(coinType, unsignedMessage.getAddress());
                key = findKeyFromPubHash(address.getHash160());
            } catch (AddressFormatException e) {
                unsignedMessage.status = SignedMessage.Status.AddressMalformed;
                return;
            }

            if (key == null) {
                unsignedMessage.status = SignedMessage.Status.MissingPrivateKey;
                return;
            }

            try {
                unsignedMessage.signature =
                        key.signMessage(coinType.getSignedMessageHeader(), message, aesKey);
                unsignedMessage.status = SignedMessage.Status.SignedOK;
            } catch (ECKey.KeyIsEncryptedException e) {
                unsignedMessage.status = SignedMessage.Status.KeyIsEncrypted;
            } catch (ECKey.MissingPrivateKeyException e) {
                unsignedMessage.status = SignedMessage.Status.MissingPrivateKey;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void verifyMessage(SignedMessage signedMessage) {
        try {
            ECKey pubKey = ECKey.signedMessageToKey(signedMessage.message, signedMessage.signature);
            byte[] expectedPubKeyHash = new Address(null, signedMessage.address).getHash160();
            if (Arrays.equals(expectedPubKeyHash, pubKey.getPubKeyHash())) {
                signedMessage.status = SignedMessage.Status.VerifiedOK;
            } else {
                signedMessage.status = SignedMessage.Status.InvalidSigningAddress;
            }
        } catch (SignatureException e) {
            signedMessage.status = SignedMessage.Status.InvalidMessageSignature;
        } catch (AddressFormatException e) {
            signedMessage.status = SignedMessage.Status.AddressMalformed;
        }
    }

    @Override
    public String getPublicKeySerialized() {
        // Change the path of the key to match the BIP32 paths i.e. 0H/<account index>H
        DeterministicKey key = keys.getWatchingKey();
        ImmutableList<ChildNumber> path = ImmutableList.of(key.getChildNumber());
        key = new DeterministicKey(path, key.getChainCode(), key.getPubKeyPoint(), null, null);
        return key.serializePubB58();
    }

    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        return findKeyFromPubHash(pubkeyHash) != null;
    }

    @Override
    public boolean isWatchedScript(Script script) {
        // Not supported
        return false;
    }

    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        return findKeyFromPubKey(pubkey) != null;
    }

    @Override
    public boolean isPayToScriptHashMine(byte[] payToScriptHash) {
        // Not supported
        return false;
    }

    public void completeAndSignTx(SendRequest request) throws InsufficientMoneyException {
        if (request.completed) {
            signTransaction(request);
        } else {
            completeTx(request);
        }
    }

    /**
     * Given a spend request containing an incomplete transaction, makes it valid by adding outputs and signed inputs
     * according to the instructions in the request. The transaction in the request is modified by this method.
     *
     * @param req a SendRequest that contains the incomplete transaction and details for how to make it valid.
     * @throws InsufficientMoneyException if the request could not be completed due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same SendRequest twice
     */
    public void completeTx(SendRequest req) throws InsufficientMoneyException {
        lock.lock();
        try {
            transactionCreator.completeTx(req);
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>Given a send request containing transaction, attempts to sign it's inputs. This method expects transaction
     * to have all necessary inputs connected or they will be ignored.</p>
     * <p>Actual signing is done by pluggable {@link org.bitcoinj.signers.LocalTransactionSigner}
     * and it's not guaranteed that transaction will be complete in the end.</p>
     */
    public void signTransaction(SendRequest req) {
        lock.lock();
        try {
            transactionCreator.signTransaction(req);
        } finally {
            lock.unlock();
        }
    }


    /**
     * Locates a keypair from the basicKeyChain given the hash of the public key. This is needed
     * when finding out which key we need to use to redeem a transaction output.
     *
     * @return ECKey object or null if no such key was found.
     */
    @Nullable
    @Override
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        lock.lock();
        try {
            return keys.findKeyFromPubHash(pubkeyHash);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Locates a keypair from the basicKeyChain given the raw public key bytes.
     * @return ECKey or null if no such key was found.
     */
    @Nullable
    @Override
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        lock.lock();
        try {
            return keys.findKeyFromPubKey(pubkey);
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] bytes) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Address getChangeAddress() {
        return currentAddress(CHANGE);
    }

    /** {@inheritDoc} */
    @Override
    public Address getReceiveAddress() {
        return currentAddress(RECEIVE_FUNDS);
    }

    /** {@inheritDoc} */
    @Override
    public Address getRefundAddress() {
        return currentAddress(REFUND);
    }

    public Address getReceiveAddress(boolean isManualAddressManagement) {
        return getAddress(RECEIVE_FUNDS, isManualAddressManagement);
    }

    public Address getRefundAddress(boolean isManualAddressManagement) {
        return getAddress(REFUND, isManualAddressManagement);
    }

    /**
     * Get the last used receiving address
     */
    @Nullable
    public Address getLastUsedAddress(SimpleHDKeyChain.KeyPurpose purpose) {
        lock.lock();
        try {
            DeterministicKey lastUsedKey = keys.getLastIssuedKey(purpose);
            if (lastUsedKey != null) {
                return lastUsedKey.toAddress(coinType);
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns true is it is possible to create new fresh receive addresses, false otherwise
     */
    public boolean canCreateFreshReceiveAddress() {
        lock.lock();
        try {
            DeterministicKey currentUnusedKey = keys.getCurrentUnusedKey(RECEIVE_FUNDS);
            int maximumKeyIndex = SimpleHDKeyChain.LOOKAHEAD - 1;

            // If there are used keys
            if (!addressesStatus.isEmpty()) {
                int lastUsedKeyIndex = 0;
                // Find the last used key index
                for (Map.Entry<Address, String> entry : addressesStatus.entrySet()) {
                    if (entry.getValue() == null) continue;
                    DeterministicKey usedKey = keys.findKeyFromPubHash(entry.getKey().getHash160());
                    if (usedKey != null && keys.isExternal(usedKey) && usedKey.getChildNumber().num() > lastUsedKeyIndex) {
                        lastUsedKeyIndex = usedKey.getChildNumber().num();
                    }
                }
                maximumKeyIndex = lastUsedKeyIndex + SimpleHDKeyChain.LOOKAHEAD;
            }

            log.info("Maximum key index for new key is {}", maximumKeyIndex);

            // If we exceeded the BIP44 look ahead threshold
            return currentUnusedKey.getChildNumber().num() < maximumKeyIndex;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get a fresh address by marking the current receive address as used. It will throw
     * {@link Bip44KeyLookAheadExceededException} if we requested too many addresses that
     * exceed the BIP44 look ahead threshold.
     */
    public Address getFreshReceiveAddress() throws Bip44KeyLookAheadExceededException {
        lock.lock();
        try {
            if (!canCreateFreshReceiveAddress()) {
                throw new Bip44KeyLookAheadExceededException();
            }
            keys.getKey(RECEIVE_FUNDS);
            return currentAddress(RECEIVE_FUNDS);
        } finally {
            lock.unlock();
            walletSaveNow();
        }
    }

    public Address getFreshReceiveAddress(boolean isManualAddressManagement) throws Bip44KeyLookAheadExceededException {
        lock.lock();
        try {
            Address newAddress = null;
            Address freshAddress = getFreshReceiveAddress();
            if (isManualAddressManagement) {
                newAddress = getLastUsedAddress(RECEIVE_FUNDS);
            }
            if (newAddress == null) {
                newAddress = freshAddress;
            }
            return newAddress;
        } finally {
            lock.unlock();
            walletSaveNow();
        }
    }

    private static final Comparator<DeterministicKey> HD_KEY_COMPARATOR =
            new Comparator<DeterministicKey>() {
                @Override
                public int compare(final DeterministicKey k1, final DeterministicKey k2) {
                    int key1Num = k1.getChildNumber().num();
                    int key2Num = k2.getChildNumber().num();
                    // In reality Integer.compare(key2Num, key1Num) but is not available on older devices
                    return (key2Num < key1Num) ? -1 : ((key2Num == key1Num) ? 0 : 1);
                }
            };

    /**
     * Returns the number of issued receiving keys
     */
    public int getNumberIssuedReceiveAddresses() {
        lock.lock();
        try {
            return keys.getNumIssuedExternalKeys();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a list of addresses that have been issued.
     * The list is sorted in descending chronological order: older in the end
     */
    public List<Address> getIssuedReceiveAddresses() {
        lock.lock();
        try {
            ArrayList<DeterministicKey> issuedKeys = keys.getIssuedExternalKeys();
            ArrayList<Address> receiveAddresses = new ArrayList<Address>();

            Collections.sort(issuedKeys, HD_KEY_COMPARATOR);

            for (ECKey key : issuedKeys) {
                receiveAddresses.add(key.toAddress(coinType));
            }
            return receiveAddresses;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the currently used receiving and change addresses
     */
    public Set<Address> getUsedAddresses() {
        lock.lock();
        try {
            HashSet<Address> usedAddresses = new HashSet<Address>();

            for (Map.Entry<Address, String> entry : addressesStatus.entrySet()) {
                if (entry.getValue() != null) {
                    usedAddresses.add(entry.getKey());
                }
            }

            return usedAddresses;
        } finally {
            lock.unlock();
        }
    }

    public Address getAddress(SimpleHDKeyChain.KeyPurpose purpose,
                              boolean isManualAddressManagement) {
        Address receiveAddress = null;
        if (isManualAddressManagement) {
            receiveAddress = getLastUsedAddress(purpose);
        }

        if (receiveAddress == null) {
            receiveAddress = currentAddress(purpose);
        }
        return receiveAddress;
    }

    /**
     * Get the currently latest unused address by purpose.
     */
    @VisibleForTesting Address currentAddress(SimpleHDKeyChain.KeyPurpose purpose) {
        lock.lock();
        try {
            return keys.getCurrentUnusedKey(purpose).toAddress(coinType);
        } finally {
            lock.unlock();
            subscribeIfNeeded();
        }
    }

    /**
     * Used to force keys creation, could take long time to complete so use it in a background
     * thread.
     */
    @VisibleForTesting void maybeInitializeAllKeys() {
        lock.lock();
        try {
            keys.maybeLookAhead();
        } finally {
            lock.unlock();
        }
    }

    public List<Address> getActiveAddresses() {
        ImmutableList.Builder<Address> activeAddresses = ImmutableList.builder();
        for (DeterministicKey key : keys.getActiveKeys()) {
            activeAddresses.add(key.toAddress(coinType));
        }
        return activeAddresses.build();
    }

    public void markAddressAsUsed(Address address) {
        keys.markPubHashAsUsed(address.getHash160());
    }

    @Override
    public String toString() {
        return WalletPocketHD.class.getSimpleName() + " " + id.substring(0, 4)+ " " + coinType;
    }
}
