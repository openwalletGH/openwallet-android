package com.coinomi.core.wallet;

import com.coinomi.core.Constants;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.crypto.KeyCrypterPin;
import com.coinomi.core.protos.Protos;
import com.coinomi.core.wallet.Wallet;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.crypto.KeyCrypterException;
import com.google.bitcoin.crypto.KeyCrypterScrypt;
import com.google.bitcoin.store.UnreadableWalletException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletProtobufSerializer {
    /**
     * <p>Parses a wallet from the given stream, using the provided Wallet instance to load data into. This is primarily
     * used when you want to register extensions. Data in the proto will be added into the wallet where applicable and
     * overwrite where not.</p>
     *
     * <p>A wallet can be unreadable for various reasons, such as inability to open the file, corrupt data, internally
     * inconsistent data, a wallet extension marked as mandatory that cannot be handled and so on. You should always
     * handle {@link UnreadableWalletException} and communicate failure to the user in an appropriate manner.</p>
     *
     * @throws UnreadableWalletException thrown in various error conditions (see description).
     */
    public Wallet readWallet(InputStream input) throws UnreadableWalletException {
        try {
            Protos.Wallet walletProto = parseToProto(input);
            return readWallet(walletProto);
        } catch (IOException e) {
            throw new UnreadableWalletException("Could not parse input stream to protobuf", e);
        }
    }

    /**
     * <p>Loads wallet data from the given protocol buffer and inserts it into the given Wallet object. This is primarily
     * useful when you wish to pre-register extension objects. Note that if loading fails the provided Wallet object
     * may be in an indeterminate state and should be thrown away.</p>
     *
     * <p>A wallet can be unreadable for various reasons, such as inability to open the file, corrupt data, internally
     * inconsistent data, a wallet extension marked as mandatory that cannot be handled and so on. You should always
     * handle {@link UnreadableWalletException} and communicate failure to the user in an appropriate manner.</p>
     *
     * @throws UnreadableWalletException thrown in various error conditions (see description).
     */
    public Wallet readWallet(Protos.Wallet walletProto) throws UnreadableWalletException {
        if (walletProto.getVersion() > 1)
            throw new UnreadableWalletException.FutureVersion();

        // Check if wallet is encrypted
        final KeyCrypter crypter = getKeyCrypter(walletProto);

        DeterministicKey masterKey =
                SimpleHDKeyChain.getDeterministicKey(walletProto.getMasterKey(), null, crypter);

        Wallet wallet = new Wallet(masterKey);

        if (walletProto.hasVersion()) {
            wallet.setVersion(walletProto.getVersion());
        }

        WalletPocketProtobufSerializer pocketSerializer = new WalletPocketProtobufSerializer();
        for (Protos.WalletPocket pocketProto : walletProto.getPocketsList()) {
            WalletPocket pocket = pocketSerializer.readWallet(pocketProto, crypter);
            wallet.addPocket(pocket);
        }

        return wallet;
    }

    private KeyCrypter getKeyCrypter(Protos.Wallet walletProto) {
        KeyCrypter crypter;
        if (walletProto.hasEncryptionType()) {
            if (walletProto.getEncryptionType() == Protos.Wallet.EncryptionType.ENCRYPTED_AES) {
                crypter = new KeyCrypterPin();
            }
            else if (walletProto.getEncryptionType() == Protos.Wallet.EncryptionType.ENCRYPTED_SCRYPT_AES) {
                checkState(walletProto.hasEncryptionParameters(), "Encryption parameters are missing");

                Protos.ScryptParameters encryptionParameters = walletProto.getEncryptionParameters();
                org.bitcoinj.wallet.Protos.ScryptParameters.Builder bitcoinjCrypter =
                        org.bitcoinj.wallet.Protos.ScryptParameters.newBuilder();
                bitcoinjCrypter.setSalt(encryptionParameters.getSalt());
                bitcoinjCrypter.setN(encryptionParameters.getN());
                bitcoinjCrypter.setP(encryptionParameters.getP());
                bitcoinjCrypter.setR(encryptionParameters.getR());

                crypter = new KeyCrypterScrypt(bitcoinjCrypter.build());
            }
            else if (walletProto.getEncryptionType() == Protos.Wallet.EncryptionType.UNENCRYPTED) {
                crypter = null;
            }
            else {
                throw new KeyCrypterException("Unsupported encryption: " + walletProto.getEncryptionType().toString());
            }
        }
        else {
            crypter = null;
        }

        return crypter;
    }


    /**
     * Returns the loaded protocol buffer from the given byte stream. You normally want
     * {@link Wallet#loadFromFile(java.io.File)} instead - this method is designed for low level work involving the
     * wallet file format itself.
     */
    public static Protos.Wallet parseToProto(InputStream input) throws IOException {
        return Protos.Wallet.parseFrom(input);
    }
}
