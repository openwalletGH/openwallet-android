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
import com.coinomi.core.coins.Value;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.wallet.families.bitcoin.BitAddress;
import com.coinomi.core.wallet.families.bitcoin.BitSendRequest;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.wallet.KeyBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.security.SignatureException;
import java.util.Arrays;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;
import static com.coinomi.core.util.BitAddressUtils.getHash160;
import static com.coinomi.core.util.BitAddressUtils.isP2SHAddress;

/**
 * @author John L. Jegutanis
 *
 *
 */
abstract public class BitWalletBase extends TransactionWatcherWallet implements KeyBag {
    private static final Logger log = LoggerFactory.getLogger(BitWalletBase.class);

    private final TransactionCreator transactionCreator;

    BitWalletBase(CoinType coinType, String id) {
        super(checkNotNull(coinType), id);
        transactionCreator = new TransactionCreator(this);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // Transaction signing support
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sends coins to the given address but does not broadcast the resulting pending transaction.
     */
    public BitSendRequest sendCoinsOffline(BitAddress address, Value amount)
            throws WalletAccountException {
        return sendCoinsOffline(address, amount, (KeyParameter) null);
    }

    /**
     * {@link #sendCoinsOffline(BitAddress, Value)}
     */
    public BitSendRequest sendCoinsOffline(BitAddress address, Value amount,
                                           @Nullable String password)
            throws WalletAccountException {
        KeyParameter key = null;
        if (password != null) {
            checkState(isEncrypted());
            key = checkNotNull(getKeyCrypter()).deriveKey(password);
        }
        return sendCoinsOffline(address, amount, key);
    }

    /**
     * {@link #sendCoinsOffline(BitAddress, Value)}
     */
    public BitSendRequest sendCoinsOffline(BitAddress address, Value amount,
                                           @Nullable KeyParameter aesKey)
            throws WalletAccountException {
        checkState(address.getParameters() instanceof CoinType);
        BitSendRequest request = BitSendRequest.to(address, amount);
        request.aesKey = aesKey;

        return request;
    }

    @Override
    public boolean isAddressMine(AbstractAddress address) {
        return address != null && address.getType().equals(type) &&
                (isP2SHAddress(address) ?
                        isPayToScriptHashMine(getHash160(address)) :
                        isPubKeyHashMine(getHash160(address)));
    }

    @Override
    public void signMessage(SignedMessage unsignedMessage, @Nullable KeyParameter aesKey) {
        String message = unsignedMessage.message;
        lock.lock();
        try {
            ECKey key;
            try {
                BitAddress address = BitAddress.from(type, unsignedMessage.getAddress());
                key = findKeyFromPubHash(address.getHash160());
            } catch (AddressMalformedException e) {
                unsignedMessage.status = SignedMessage.Status.AddressMalformed;
                return;
            }

            if (key == null) {
                unsignedMessage.status = SignedMessage.Status.MissingPrivateKey;
                return;
            }

            try {
                unsignedMessage.signature =
                        key.signMessage(type.getSignedMessageHeader(), message, aesKey);
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
            ECKey pubKey = ECKey.signedMessageToKey(
                    type.getSignedMessageHeader(), signedMessage.message, signedMessage.signature);
            byte[] expectedPubKeyHash = BitAddress.from(type, signedMessage.address).getHash160();
            if (Arrays.equals(expectedPubKeyHash, pubKey.getPubKeyHash())) {
                signedMessage.status = SignedMessage.Status.VerifiedOK;
            } else {
                signedMessage.status = SignedMessage.Status.InvalidSigningAddress;
            }
        } catch (SignatureException e) {
            signedMessage.status = SignedMessage.Status.InvalidMessageSignature;
        } catch (AddressMalformedException e) {
            signedMessage.status = SignedMessage.Status.AddressMalformed;
        }
    }

    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        return findKeyFromPubHash(pubkeyHash) != null;
    }

    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        return findKeyFromPubKey(pubkey) != null;
    }

    @Override
    public SendRequest getEmptyWalletRequest(AbstractAddress destination)
            throws WalletAccountException {
        checkAddress(destination);
        return BitSendRequest.emptyWallet((BitAddress) destination);
    }

    @Override
    public SendRequest getSendToRequest(AbstractAddress destination, Value amount)
            throws WalletAccountException {
        checkAddress(destination);
        return BitSendRequest.to((BitAddress) destination, amount);
    }

    private void checkAddress(AbstractAddress destination) throws WalletAccountException {
        if (!(destination instanceof BitAddress)) {
            throw new WalletAccountException("Incompatible address" +
                    destination.getClass().getName() + ", expected " + BitAddress.class.getName());
        }
    }

    @Override
    public void completeTransaction(SendRequest request) throws WalletAccountException {
        checkSendRequest(request);
        completeTransaction((BitSendRequest) request);
    }

    @Override
    public void signTransaction(SendRequest request) throws WalletAccountException {
        checkSendRequest(request);
        signTransaction((BitSendRequest) request);
    }


    private void checkSendRequest(SendRequest request) throws WalletAccountException {
        if (!(request instanceof BitSendRequest)) {
            throw new WalletAccountException("Incompatible request " +
                    request.getClass().getName() + ", expected " + BitSendRequest.class.getName());
        }
    }

    /**
     * Given a spend request containing an incomplete transaction, makes it valid by adding outputs and signed inputs
     * according to the instructions in the request. The transaction in the request is modified by this method.
     *
     * @param req a BitSendRequest that contains the incomplete transaction and details for how to make it valid.
     * @throws WalletAccountException if the request could not be completed due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same SendRequest twice
     */
    public void completeTransaction(BitSendRequest req) throws WalletAccountException {
        lock.lock();
        try {
            transactionCreator.completeTx(req);
        } catch (InsufficientMoneyException e) {
            throw new WalletAccountException(e);
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
    public void signTransaction(BitSendRequest req) {
        lock.lock();
        try {
            transactionCreator.signTransaction(req);
        } finally {
            lock.unlock();
        }
    }
}
