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
    public static final Pattern PATTERN_MINI_PRIVATE_KEY = Pattern.compile("S[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{29}");

    public static class TypedKey {
        public final List<CoinType> possibleType;
        public final ECKey key;

        TypedKey(List<CoinType> possibleType, ECKey key) {
            this.possibleType = possibleType;
            this.key = key;
        }
    }

    enum Type {
        WIF, BIP38, MINI
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

    private Type keyType;
    private int version;
    private boolean ecMultiply;
    private boolean compressed;
    private boolean hasLotAndSequence;
    private byte[] addressHash;
    private byte[] content;

    public SerializedKey(String key) throws KeyFormatException {
        if (PATTERN_WIF_PRIVATE_KEY.matcher(key).matches()) {
            parseWif(key);
            keyType = Type.WIF;
        } else if (PATTERN_BIP38_PRIVATE_KEY.matcher(key).matches()) {
            parseBip38(key);
            keyType = Type.BIP38;
        } else if (PATTERN_MINI_PRIVATE_KEY.matcher(key).matches()) {
            parseMini(key);
            keyType = Type.MINI;
        } else {
            throw new KeyFormatException("Unknown key format.");
        }
    }

    public static boolean isSerializedKey(String key) {
        return PATTERN_WIF_PRIVATE_KEY.matcher(key).matches() ||
                PATTERN_BIP38_PRIVATE_KEY.matcher(key).matches() ||
                PATTERN_MINI_PRIVATE_KEY.matcher(key).matches();
    }

    private void parseWif(String key) throws KeyFormatException {
        byte[] keyBytes = parseBase58(key);

        // Check if compatible
        boolean isCompatible = false;
        for (CoinType type : CoinID.getSupportedCoins()) {
            if (version == type.getDumpedPrivateKeyHeader()) {
                isCompatible = true;
            }
        }
        if (!isCompatible) {
            clearDataAndThrow(keyBytes, "No coin with private key version: " + version);
        }

        if (keyBytes.length == 33 && keyBytes[32] == 1) {
            compressed = true;
            content = Arrays.copyOf(keyBytes, 32);  // Chop off the additional marker byte.
            clearData(keyBytes);
        } else if (keyBytes.length == 32) {
            compressed = false;
            content = keyBytes;
        } else {
            clearDataAndThrow(keyBytes, "Wrong number of bytes for a private key, not 32 or 33");
        }
    }

    private void parseBip38(String key) throws KeyFormatException {
        byte[] bytes = parseBase58(key);
        if (version != 0x01)
            clearDataAndThrow(bytes, "Mismatched version number: " + version);
        if (bytes.length != 38)
            clearDataAndThrow(bytes, "Wrong number of bytes, excluding version byte: " + bytes.length);
        hasLotAndSequence = (bytes[1] & 0x04) != 0; // bit 2
        compressed = (bytes[1] & 0x20) != 0; // bit 5
        if ((bytes[1] & 0x01) != 0) // bit 0
            clearDataAndThrow(bytes, "Bit 0x01 reserved for future use.");
        if ((bytes[1] & 0x02) != 0) // bit 1
            clearDataAndThrow(bytes, "Bit 0x02 reserved for future use.");
        if ((bytes[1] & 0x08) != 0) // bit 3
            clearDataAndThrow(bytes, "Bit 0x08 reserved for future use.");
        if ((bytes[1] & 0x10) != 0) // bit 4
            clearDataAndThrow(bytes, "Bit 0x10 reserved for future use.");
        final int byte0 = bytes[0] & 0xff;
        if (byte0 == 0x42) {
            // Non-EC-multiplied key
            if ((bytes[1] & 0xc0) != 0xc0) // bits 6+7
                clearDataAndThrow(bytes, "Bits 0x40 and 0x80 must be set for non-EC-multiplied keys.");
            ecMultiply = false;
            if (hasLotAndSequence)
                clearDataAndThrow(bytes, "Non-EC-multiplied keys cannot have lot/sequence.");
        } else if (byte0 == 0x43) {
            // EC-multiplied key
            if ((bytes[1] & 0xc0) != 0x00) // bits 6+7
                clearDataAndThrow(bytes, "Bits 0x40 and 0x80 must be cleared for EC-multiplied keys.");
            ecMultiply = true;
        } else {
            clearDataAndThrow(bytes, "Second byte must by 0x42 or 0x43.");
        }
        addressHash = Arrays.copyOfRange(bytes, 2, 6);
        content = Arrays.copyOfRange(bytes, 6, 38);
        clearData(bytes);
    }

    private void parseMini(String key) throws KeyFormatException {
        byte[] bytes = key.getBytes();
        byte[] checkBytes = new byte[31]; // 30 chars + '?'
        List<byte[]> allBytes = ImmutableList.of(bytes, checkBytes);

        if (!key.startsWith("S")) {
            clearDataAndThrow(allBytes, "Mini private keys must start with 'S'");
        }
        if (bytes.length != 30) {
            clearDataAndThrow(allBytes, "Mini private keys must be 30 characters long");
        }

        System.arraycopy(bytes, 0, checkBytes, 0, 30);
        checkBytes[30] = '?';
        // Check if the sha256 hash of key + "?" starts with 0x00
        if (Sha256Hash.create(checkBytes).getBytes()[0] != 0x00) {
            clearDataAndThrow(allBytes, "Not well formed mini private key");
        }
        compressed = false; // Mini keys are not compressed
        content = Sha256Hash.create(bytes).getBytes();
        clearData(allBytes);
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
        clearData(versionAndDataBytes);
        return payload;
    }

    private void clearDataAndThrow(List<byte[]> bytes, String message) throws KeyFormatException {
        clearData(bytes);
        throw new KeyFormatException(message);
    }

    private void clearDataAndThrow(byte[] bytes, String message) throws KeyFormatException {
        clearData(bytes);
        throw new KeyFormatException(message);
    }

    private static void clearData(List<byte[]> bytes) {
        for (byte[] b : bytes) {
            clearData(b);
        }
    }

    private static void clearData(byte[] bytes) {
        Arrays.fill(bytes, (byte) 0);
    }

    public boolean isEncrypted() {
        switch (keyType) {
            case WIF:
                return false;
            case BIP38:
                return true;
            case MINI:
                return false;
            default:
                throw new RuntimeException("Unknown key format."); // Should not happen
        }
    }

    public TypedKey getKey() throws BadPassphraseException {
        return getKey(null);
    }

    public TypedKey getKey(@Nullable String passphrase) throws BadPassphraseException {
        switch (keyType) {
            case WIF:
                return getFromWifKey();
            case BIP38:
                return decryptBip38(passphrase);
            case MINI:
                return getFromMiniKey();
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

    private TypedKey getFromMiniKey() {
        final ECKey key = ECKey.fromPrivate(content).decompress();
        return new TypedKey(CoinID.getSupportedCoins(), key);
    }
}
