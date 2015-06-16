package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.RedeemData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.List;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 * @author John L. Jegutanis
 */
public class AddressWallet extends AbstractWallet {
    private static final Logger log = LoggerFactory.getLogger(AddressWallet.class);

    private final Address address;

    public AddressWallet(CoinType coinType, Address address) {
        super(coinType, Utils.HEX.encode(address.getHash160()));
        this.address = address;
    }

    @Override
    public Address getChangeAddress() {
        return null;
    }

    @Override
    public Address getReceiveAddress() {
        return null;
    }

    @Override
    public Address getRefundAddress() {
        return null;
    }

    @Override
    public List<Address> getActiveAddresses() {
        return null;
    }

    @Override
    public void markAddressAsUsed(Address address) {

    }

    @Override
    public boolean isEncryptable() {
        return false;
    }

    @Override
    public boolean isEncrypted() {
        return false;
    }

    @Override
    public KeyCrypter getKeyCrypter() {
        return null;
    }

    @Override
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {

    }

    @Override
    public void decrypt(KeyParameter aesKey) {

    }

    @Override
    public boolean equals(WalletAccount otherAccount) {
        return false;
    }

    @Override
    public boolean isAddressMine(Address address) {
        return false;
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubHash(byte[] pubkeyHash) {
        return null;
    }

    @Nullable
    @Override
    public ECKey findKeyFromPubKey(byte[] pubkey) {
        return null;
    }

    @Nullable
    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
        return null;
    }

    @Override
    public boolean isPubKeyHashMine(byte[] pubkeyHash) {
        return false;
    }

    @Override
    public boolean isWatchedScript(Script script) {
        return false;
    }

    @Override
    public boolean isPubKeyMine(byte[] pubkey) {
        return false;
    }

    @Override
    public boolean isPayToScriptHashMine(byte[] payToScriptHash) {
        return false;
    }
}
