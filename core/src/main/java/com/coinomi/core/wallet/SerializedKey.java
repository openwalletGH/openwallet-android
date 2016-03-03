package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.exceptions.AddressMalformedException;
import com.coinomi.core.util.GenericUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;
import com.lambdaworks.crypto.SCrypt;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DRMWorkaround;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import static com.google.common.base.Preconditions.checkState;

/**
 * @author John L. Jegutanis
 * @author Andreas Schildbach
 */
public class SerializedKey implements Serializable {

    public static final Pattern PATTERN_WIF_PRIVATE_KEY = Pattern.compile("[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{51,52}");
    public static final Pattern PATTERN_BIP38_PRIVATE_KEY = Pattern.compile("6P[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{56}");

    public static class TypedKey {
        public final List<CoinType> possibleType;
        public final ECKey key;

        TypedKey(List<CoinType> possibleType, ECKey key) {
            this.possibleType = possibleType;
            this.key = key;
        }
    }

    enum Type {
        WIF, BIP38
    }

    public static final class BadPassphraseException extends Exception { }
    public static final class KeyFormatException extends Exception {
        public KeyFormatException(String message) {
            super(message);
        }

        public KeyFormatException(Throwable cause) {
            super(cause);
        }
    }

    private Type type;
    private int version;
    private boolean ecMultiply;
    private boolean compressed;
    private boolean hasLotAndSequence;
    private byte[] addressHash;
    private byte[] content;

    public SerializedKey(String key) throws KeyFormatException {
        if (PATTERN_WIF_PRIVATE_KEY.matcher(key).matches()) {
            parseWif(key);
            type = Type.WIF;
        } else if (PATTERN_BIP38_PRIVATE_KEY.matcher(key).matches()) {
            parseBip38(key);
            type = Type.BIP38;
        } else {
            throw new KeyFormatException("Unknown key format.");
        }
    }

    public static boolean isSerializedKey(String key) {
        if (PATTERN_WIF_PRIVATE_KEY.matcher(key).matches() ||
                PATTERN_BIP38_PRIVATE_KEY.matcher(key).matches()) {
            return true;
        } else {
            return false;
        }
    }

    private void parseWif(String key) throws KeyFormatException {
        content = parseBase58(key);

        // Check if compatible
        boolean isCompatible = false;
        for (CoinType type : CoinID.getSupportedCoins()) {
            if (version == type.getDumpedPrivateKeyHeader()) {
                isCompatible = true;
            }
        }
        if (!isCompatible) {
            throw new KeyFormatException("No coin with private key version: " + version);
        }

        if (content.length == 33 && content[32] == 1) {
            compressed = true;
            byte[] newContent = Arrays.copyOf(content, 32);  // Chop off the additional marker byte.
            clearSensitiveData(content);
            content = newContent;
        } else if (content.length == 32) {
            compressed = false;
        } else {
            clearSensitiveData(content);
            throw new KeyFormatException("Wrong number of bytes for a private key, not 32 or 33");
        }
    }

    private void parseBip38(String key) throws KeyFormatException {
        byte[] bytes = parseBase58(key);
        if (version != 0x01)
            throw new KeyFormatException("Mismatched version number: " + version);
        if (bytes.length != 38)
            throw new KeyFormatException("Wrong number of bytes, excluding version byte: " + bytes.length);
        hasLotAndSequence = (bytes[1] & 0x04) != 0; // bit 2
        compressed = (bytes[1] & 0x20) != 0; // bit 5
        if ((bytes[1] & 0x01) != 0) // bit 0
            throw new KeyFormatException("Bit 0x01 reserved for future use.");
        if ((bytes[1] & 0x02) != 0) // bit 1
            throw new KeyFormatException("Bit 0x02 reserved for future use.");
        if ((bytes[1] & 0x08) != 0) // bit 3
            throw new KeyFormatException("Bit 0x08 reserved for future use.");
        if ((bytes[1] & 0x10) != 0) // bit 4
            throw new KeyFormatException("Bit 0x10 reserved for future use.");
        final int byte0 = bytes[0] & 0xff;
        if (byte0 == 0x42) {
            // Non-EC-multiplied key
            if ((bytes[1] & 0xc0) != 0xc0) // bits 6+7
                throw new KeyFormatException("Bits 0x40 and 0x80 must be set for non-EC-multiplied keys.");
            ecMultiply = false;
            if (hasLotAndSequence)
                throw new KeyFormatException("Non-EC-multiplied keys cannot have lot/sequence.");
        } else if (byte0 == 0x43) {
            // EC-multiplied key
            if ((bytes[1] & 0xc0) != 0x00) // bits 6+7
                throw new KeyFormatException("Bits 0x40 and 0x80 must be cleared for EC-multiplied keys.");
            ecMultiply = true;
        } else {
            throw new KeyFormatException("Second byte must by 0x42 or 0x43.");
        }
        addressHash = Arrays.copyOfRange(bytes, 2, 6);
        content = Arrays.copyOfRange(bytes, 6, 38);
        clearSensitiveData(bytes);
    }

    private byte[] parseBase58(String key) throws KeyFormatException {
        byte[] versionAndDataBytes;
        try {
            versionAndDataBytes = Base58.decodeChecked(key);
        } catch (AddressFormatException e) {
            throw new KeyFormatException(e);
        }
        version = versionAndDataBytes[0] & 0xFF;
        byte[] payload = new byte[versionAndDataBytes.length - 1];
        System.arraycopy(versionAndDataBytes, 1, payload, 0, versionAndDataBytes.length - 1);
        clearSensitiveData(versionAndDataBytes);
        return payload;
    }

    private static void clearSensitiveData(byte[] bytes) {
        Arrays.fill(bytes, (byte) 0);
    }

    public boolean isEncrypted() {
        switch (type) {
            case WIF:
                return false;
            case BIP38:
                return true;
            default:
                throw new RuntimeException("Unknown key format."); // Should not happen
        }
    }

    public TypedKey getKey() throws BadPassphraseException {
        return getKey(null);
    }

    public TypedKey getKey(@Nullable String passphrase) throws BadPassphraseException {
        switch (type) {
            case WIF:
                return getFromWifKey();
            case BIP38:
                return decryptBip38(passphrase);
            default:
                throw new RuntimeException("Unknown key format."); // Should not happen
        }
    }

    private TypedKey getFromWifKey() {
        ImmutableList.Builder<CoinType> builder = ImmutableList.builder();
        for (CoinType type : CoinID.getSupportedCoins()) {
            if (version == type.getDumpedPrivateKeyHeader()) {
                builder.add(type);
            }
        }
        final ECKey key = compressed ? ECKey.fromPrivate(content) : ECKey.fromPrivate(content).decompress();
        return new TypedKey(builder.build(), key);
    }


    public TypedKey decryptBip38(String passphrase) throws BadPassphraseException {
        String normalizedPassphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFC);
        ECKey key = ecMultiply ? decryptBip38EC(normalizedPassphrase) : decryptBip38NoEC(normalizedPassphrase);
        String address = null;
        for (CoinType type : CoinID.getSupportedCoins()) {
            String possibleAddress = key.toAddress(type).toString();
            Sha256Hash hash = Sha256Hash.createDouble(possibleAddress.getBytes(Charsets.US_ASCII));
            byte[] actualAddressHash = Arrays.copyOfRange(hash.getBytes(), 0, 4);
            if (Arrays.equals(actualAddressHash, addressHash)) {
                address = possibleAddress;
            }
        }
        if (address == null) {
            throw new BadPassphraseException();
        }
        try {
            return new TypedKey(GenericUtils.getPossibleTypes(address), key);
        } catch (AddressMalformedException e) {
            throw new RuntimeException(e); // Should not happen
        }
    }

    private ECKey decryptBip38NoEC(String normalizedPassphrase) {
        try {
            byte[] derived = SCrypt.scrypt(normalizedPassphrase.getBytes(Charsets.UTF_8), addressHash, 16384, 8, 8, 64);
            byte[] key = Arrays.copyOfRange(derived, 32, 64);
            SecretKeySpec keyspec = new SecretKeySpec(key, "AES");

            DRMWorkaround.maybeDisableExportControls();
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");

            cipher.init(Cipher.DECRYPT_MODE, keyspec);
            byte[] decrypted = cipher.doFinal(content, 0, 32);
            for (int i = 0; i < 32; i++)
                decrypted[i] ^= derived[i];
            return ECKey.fromPrivate(decrypted, compressed);
        } catch (GeneralSecurityException x) {
            throw new RuntimeException(x);
        }
    }

    private ECKey decryptBip38EC(String normalizedPassphrase) {
        try {
            byte[] ownerEntropy = Arrays.copyOfRange(content, 0, 8);
            byte[] ownerSalt = hasLotAndSequence ? Arrays.copyOfRange(ownerEntropy, 0, 4) : ownerEntropy;

            byte[] passFactorBytes = SCrypt.scrypt(normalizedPassphrase.getBytes(Charsets.UTF_8), ownerSalt, 16384, 8, 8, 32);
            if (hasLotAndSequence) {
                byte[] hashBytes = Bytes.concat(passFactorBytes, ownerEntropy);
                checkState(hashBytes.length == 40);
                passFactorBytes = Sha256Hash.createDouble(hashBytes).getBytes();
            }
            BigInteger passFactor = new BigInteger(1, passFactorBytes);
            ECKey k = ECKey.fromPrivate(passFactor, true);

            byte[] salt = Bytes.concat(addressHash, ownerEntropy);
            checkState(salt.length == 12);
            byte[] derived = SCrypt.scrypt(k.getPubKey(), salt, 1024, 1, 1, 64);
            byte[] aeskey = Arrays.copyOfRange(derived, 32, 64);

            SecretKeySpec keyspec = new SecretKeySpec(aeskey, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keyspec);

            byte[] encrypted2 = Arrays.copyOfRange(content, 16, 32);
            byte[] decrypted2 = cipher.doFinal(encrypted2);
            checkState(decrypted2.length == 16);
            for (int i = 0; i < 16; i++)
                decrypted2[i] ^= derived[i + 16];

            byte[] encrypted1 = Bytes.concat(Arrays.copyOfRange(content, 8, 16), Arrays.copyOfRange(decrypted2, 0, 8));
            byte[] decrypted1 = cipher.doFinal(encrypted1);
            checkState(decrypted1.length == 16);
            for (int i = 0; i < 16; i++)
                decrypted1[i] ^= derived[i];

            byte[] seed = Bytes.concat(decrypted1, Arrays.copyOfRange(decrypted2, 8, 16));
            checkState(seed.length == 24);
            BigInteger seedFactor = new BigInteger(1, Sha256Hash.createDouble(seed).getBytes());
            checkState(passFactor.signum() >= 0);
            checkState(seedFactor.signum() >= 0);
            BigInteger priv = passFactor.multiply(seedFactor).mod(ECKey.CURVE.getN());

            return ECKey.fromPrivate(priv, compressed);
        } catch (GeneralSecurityException x) {
            throw new RuntimeException(x);
        }
    }
}
