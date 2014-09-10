package com.coinomi.core.wallet;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinTest;
import com.coinomi.core.crypto.KeyCrypterPin;
import com.coinomi.core.network.AddressStatus;
import com.coinomi.core.network.interfaces.BlockchainConnection;
import com.coinomi.core.network.ServerClient;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.crypto.KeyCrypter;
import com.google.bitcoin.utils.BriefLogFormatter;
import com.google.bitcoin.wallet.DeterministicSeed;
import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.spongycastle.crypto.params.KeyParameter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletPocketTest {
    static final List<String> MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");
    static final byte[] aesKeyBytes = {0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 1, 2, 3, 4, 5, 6, 7};
    DeterministicSeed seed = new DeterministicSeed(MNEMONIC, "", 0);
    private DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
    CoinType type = DogecoinTest.get();
    DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
    DeterministicKey rootKey = hierarchy.get(type.getBip44Path(0), false, true);
    WalletPocket pocket;
    KeyParameter aesKey = new KeyParameter(aesKeyBytes);
    KeyCrypter crypter = new KeyCrypterPin();

    @Before
    public void setup() {
        BriefLogFormatter.init();

        pocket = new WalletPocket(rootKey, type, null);
        pocket.keys.setLookaheadSize(20);
    }

    @Test
    public void watchingAddresses() {
        List<Address> watchingAddresses = pocket.getAddressesToWatch();
        assertEquals(40, watchingAddresses.size()); // 20 + 20 lookahead size
        for (int i = 0; i < addresses.length; i++) {
            assertEquals(addresses[i], watchingAddresses.get(i).toString());
        }
    }

    @Test
    public void fillTransactions() throws Exception {
        pocket.onConnection(getBlockchainConnection());

        // Issued keys
        assertEquals(18, pocket.keys.getIssuedExternalKeys());
        assertEquals(9, pocket.keys.getIssuedInternalKeys());

        // Transactions
        assertEquals(13, pocket.addressToTransaction.size());

        Set<Transaction> addressTxs = new HashSet<Transaction>();
        for (TransactionMap txMap : pocket.addressToTransaction.values()) {
            addressTxs.addAll(txMap.values());
        }

        assertEquals(pocket.unspent.size(), addressTxs.size());

        // No addresses left to subscribe
        List<Address> addressesToWatch = pocket.getAddressesToWatch();
        assertEquals(0, addressesToWatch.size());

        // 18 external issued + 20 lookahead +  9 external issued + 20 lookahead
        assertEquals(67, pocket.addressesStatus.size());
        assertEquals(67, pocket.addressesSubscribed.size());

        Address receiveAddr = pocket.getReceiveAddress();
        // This key is not issued
        assertEquals(18, pocket.keys.getIssuedExternalKeys());
        assertEquals(67, pocket.addressesStatus.size());
        assertEquals(67, pocket.addressesSubscribed.size());

        DeterministicKey key = pocket.keys.findKeyFromPubHash(receiveAddr.getHash160());
        assertNotNull(key);
        // 18 here is the key index, not issued keys count
        assertEquals(18, key.getChildNumber().num());


        // TODO added more tests to insure it uses the "holes" in the keychain
    }

    @Test
    public void serializeUnencryptedNormal() throws Exception {
        pocket.onConnection(getBlockchainConnection());

        Protos.WalletPocket walletPocketProto = pocket.toProtobuf();

        WalletPocket newPocket = new WalletPocketProtobufSerializer().readWallet(walletPocketProto, null);

        assertEquals(pocket.getCoinType(), newPocket.getCoinType());
        assertEquals(pocket.getDescription(), newPocket.getDescription());
        assertEquals(pocket.keys.toProtobuf().toString(), newPocket.keys.toProtobuf().toString());
        assertEquals(pocket.getLastBlockSeenHash(), newPocket.getLastBlockSeenHash());
        assertEquals(pocket.getLastBlockSeenHeight(), newPocket.getLastBlockSeenHeight());
        assertEquals(pocket.getLastBlockSeenTimeSecs(), newPocket.getLastBlockSeenTimeSecs());

        for (Transaction tx : pocket.getTransactions(true)) {
            assertEquals(tx, newPocket.getTransaction(tx.getHash()));
        }

        for (AddressStatus status : pocket.getAllAddressStatus()) {
            if (status.getStatus() == null) continue;
            assertEquals(status, newPocket.getAddressStatus(status.getAddress()));
        }


        // Issued keys
        assertEquals(18, newPocket.keys.getIssuedExternalKeys());
        assertEquals(9, newPocket.keys.getIssuedInternalKeys());

        newPocket.onConnection(getBlockchainConnection());

        // No addresses left to subscribe
        List<Address> addressesToWatch = newPocket.getAddressesToWatch();
        assertEquals(0, addressesToWatch.size());

        // 18 external issued + 20 lookahead +  9 external issued + 20 lookahead
        assertEquals(67, newPocket.addressesStatus.size());
        assertEquals(67, newPocket.addressesSubscribed.size());
    }

    @Test
    public void serializeUnencryptedEmpty() throws Exception {
        pocket.initializeAllKeysIfNeeded();
        Protos.WalletPocket walletPocketProto = pocket.toProtobuf();

        WalletPocket newPocket = new WalletPocketProtobufSerializer().readWallet(walletPocketProto, null);

        assertEquals(walletPocketProto.toString(), newPocket.toProtobuf().toString());

        // Issued keys
        assertEquals(0, newPocket.keys.getIssuedExternalKeys());
        assertEquals(0, newPocket.keys.getIssuedInternalKeys());

        // 20 lookahead + 20 lookahead
        assertEquals(40, newPocket.keys.getLeafKeys().size());
    }


    @Test
    public void serializeEncryptedEmpty() throws Exception {
        pocket.initializeAllKeysIfNeeded();
        pocket.encrypt(crypter, aesKey);

        Protos.WalletPocket walletPocketProto = pocket.toProtobuf();

        WalletPocket newPocket = new WalletPocketProtobufSerializer().readWallet(walletPocketProto, crypter);

        assertEquals(walletPocketProto.toString(), newPocket.toProtobuf().toString());

        pocket.decrypt(aesKey);

        // One is encrypted, so they should not match
        assertNotEquals(pocket.toProtobuf().toString(), newPocket.toProtobuf().toString());

        newPocket.decrypt(aesKey);

        assertEquals(pocket.toProtobuf().toString(), newPocket.toProtobuf().toString());
    }

    @Test
    public void createTransaction() throws Exception {
        pocket.onConnection(getBlockchainConnection());

        Address toAddr = new Address(type, "nUEkQ3LjH9m4ScbP6NGtnAdnnUsdtWv99Q");

        Transaction tx = pocket.sendCoinsOffline(toAddr, Coin.valueOf(2700000000L));

        Transaction txExpected = new Transaction(type, Utils.HEX.decode(expectedTx));

        // FIXME
        assertEquals(expectedTx, Utils.HEX.encode(tx.bitcoinSerialize()));
    }



    // Util methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    HashMap<Address, AddressStatus> getDummyStatuses() throws AddressFormatException {
        HashMap<Address, AddressStatus> status = new HashMap<Address, AddressStatus>(40);

        for (int i = 0; i < addresses.length; i++) {
            Address address = new Address(type, addresses[i]);
            status.put(address, new AddressStatus(address, statuses[i]));
        }

        return status;
    }

    private HashMap<Address,List<ServerClient.UnspentTx>> getDummyUTXs() throws AddressFormatException, JSONException {
        HashMap<Address, List<ServerClient.UnspentTx>> utxs = new HashMap<Address, List<ServerClient.UnspentTx>>(40);

        for (int i = 0; i < statuses.length; i++) {
            utxs.put(new Address(type, addresses[i]), ServerClient.UnspentTx.fromArray(new JSONArray(unspent[i])));
        }

        return utxs;
    }

    private HashMap<Sha256Hash, byte[]> getDummyRawTXs() throws AddressFormatException, JSONException {
        HashMap<Sha256Hash, byte[]> rawTxs = new HashMap<Sha256Hash, byte[]>();

        for (int i = 0; i < txs.length; i++) {
            String[] txEntry = txs[i];
            rawTxs.put(new Sha256Hash(txs[i][0]), Utils.HEX.decode(txs[i][1]));
        }

        return rawTxs;
    }

    class MockBlockchainConnection implements BlockchainConnection {
        final HashMap<Address, AddressStatus> statuses;
        final HashMap<Address, List<ServerClient.UnspentTx>> utxs;
        final HashMap<Sha256Hash, byte[]> rawTxs;

        MockBlockchainConnection() throws Exception {
            statuses = getDummyStatuses();
            utxs = getDummyUTXs();
            rawTxs = getDummyRawTXs();
        }

        @Override
        public void subscribeToAddresses(CoinType coin, List<Address> addresses, TransactionEventListener listener) {
            for (Address a : addresses) {
                AddressStatus status = statuses.get(a);
                if (status == null) {
                    status = new AddressStatus(a, null);
                }
                listener.onAddressStatusUpdate(status);
            }
        }

        @Override
        public void getUnspentTx(CoinType coinType, AddressStatus status, TransactionEventListener listener) {
            List<ServerClient.UnspentTx> utx = utxs.get(status.getAddress());
            if (status == null) {
                utx = ImmutableList.of();
            }
            listener.onUnspentTransactionUpdate(status, utx);
        }

        @Override
        public void getTx(CoinType coinType, AddressStatus status, ServerClient.UnspentTx utx, TransactionEventListener listener) {
            listener.onTransactionUpdate(status, utx, rawTxs.get(utx.getTxHash()));
        }

        @Override
        public void broadcastTx(CoinType coinType, Transaction tx, TransactionEventListener listener) {

        }

        @Override
        public void ping() {}
    }

    private MockBlockchainConnection getBlockchainConnection() throws Exception {
        return new MockBlockchainConnection();
    }

    // Mock data
    String[] addresses = {
            "nnfP8VuPfZXhNtMDzvX1bKurzcV1k7HNrQ",
            "nf4AUKiaGdx4GTbbh222KvtuCbAuvbcdE2",
            "npGkmbWtdWybFNSZQXUK6zZxbEocMtrTzz",
            "nVaN45bbs6AUc1EUpP71hHGBGb2qciNrJc",
            "nrdHFZP1AfdKBrjsSQmwFm8R2i2mzMef75",
            "niGZgfbhFYn6tJqksmC8CnzSRL1GHNsu7e",
            "nh6w8yu1zoKYoT837ffkVmuPjTaP69Pc5E",
            "nbyMgmEghsL9tpk7XfdH9gLGudh6Lrbbuf",
            "naX9akzYuWY1gKbcZo3t36aBKc1gqbzgSs",
            "nqcPVTGeAfCowELB2D5PdVF3FWFjFtkkFf",
            "nd4vVvPTpp2LfcsMPsmG3Dh7MFAsqRHp4g",
            "nVK4Uz5Sf56ygrr6RiwXcRvH8AuUVbjjHi",
            "nbipkRwT1NCSXZSrm8uAAEgQAA2s2NWMkG",
            "nZU6QAMAdCQcVDxEmL7GEY6ykFm8m6u6am",
            "nbFqayssM5s7hLjLFwf2d95JXKRWBk2pBH",
            "nacZfcNgVi47hamLscWdUGQhUQGGpSdgq8",
            "niiMT7ZYeFnaLPYtqQFBBX1sP3dT5JtcEw",
            "ns6GyTaniLWaiYrvAf5tk4D8tWrbcCLGyj",
            "nhdwQfptLTzLJGbLF3vvtqyBaqPMPecDmE",
            "neMUAGeTzwxLbSkXMsdmWf1fTKS1UsJjXY",
            "nXsAMeXxtpx8jaxnU3Xv9ZQ6ZcRcD1xYhR",
            "ns35rKnnWf6xP3KSj5WPkMCVVaADGi6Ndk",
            "nk4wcXYBEWs5HkhNLsuaQJoAjJHoK6SQmG",
            "npsJQWu8fsALoTPum8D4j8FDkyeusk8fU8",
            "nZNhZo4Vz3DnwQGPL3SJTZgUw2Kh4g9oay",
            "nnxDTYB8WMHMgLnCY2tuaEtysvdhRnWehZ",
            "nb2iaDXG1EqkcE47H87WQFkQNkeVK66Z21",
            "nWHAkpn2DB3DJRsbiK3vpbigoa3M2uVuF8",
            "nViKdC7Gm6TMmCuWTBwVE9i4rJhyfwbfqg",
            "nZQV5BifbGPzaxTrB4efgHruWH5rufemqP",
            "nVvZaLvnkCVAzpLYPoHeqU4w9zJ5yrZgUn",
            "nrMp6pRCk98WYWkCWq9Pqthz9HbpQu8BT3",
            "nnA3aYsLqysKT6gAu1dr4EKm586cmKiRxS",
            "nVfcVgMY7DL6nqoSxwJy7B7hKXirQwj6ic",
            "ni4oAzi6nCVuEdjoHyVMVKWb1DqTd3qY3H",
            "nnpf3gx442yomniRJPMGPapgjHrraPZXxJ",
            "nkuFnF8wUmHFkMycaFMvyjBoiMeR5KBKGd",
            "nXKccwjaUyrQkLrdqKT6aq6cDiFgBBVgNz",
            "nZMSNsXSAL7i1YD6KP5FrhATuZ2CWvnxqR",
            "nUEkQ3LjH9m4ScbP6NGtnAdnnUsdtWv99Q"
    };

    String[] statuses = {
            "fe7c109d8bd90551a406cf0b3499117db04bc9c4f48e1df27ac1cf3ddcb3d464",
            "8a53babd831c6c3a857e20190e884efe75a005bdd7cd273c4f27ab1b8ec81c2d",
            "86bc2f0cf0112fd59c9aadfe5c887062c21d7a873db260dff68dcfe4417fe212",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            "64a575b5605671831185ca715e8197f0455733e721a6c6c5b8add31bd6eabbe9",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
    };


    String[] unspent = {
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 0, \"value\": 500000000, \"height\": 160267}, {\"tx_hash\": \"89a72ba4732505ce9b09c30668db985952701252ce0adbd7c43336396697d6ae\", \"tx_pos\": 0, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 1, \"value\": 1000000000, \"height\": 160267}, {\"tx_hash\": \"edaf445288d8e65cf7963bc8047c90f53681acaadc5ccfc5ecc67aedbd73cddb\", \"tx_pos\": 0, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 2, \"value\": 500000000, \"height\": 160267}, {\"tx_hash\": \"81a1f0f8242d5e71e65ff9e8ec51e8e85d641b607d7f691c1770d4f25918ebd7\", \"tx_pos\": 0, \"value\": 1000000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 3, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 4, \"value\": 1000000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 11, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 12, \"value\": 1000000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 13, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 6, \"value\": 500000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 7, \"value\": 1000000000, \"height\": 160267}]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 8, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 9, \"value\": 500000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[{\"tx_hash\": \"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f\", \"tx_pos\": 10, \"value\": 1000000000, \"height\": 160267}]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]",
            "[]"
    };

    String[][] txs = {
            {"ef74da273e8a77e2d60b707414fb7e0ccb35c7b1b936800a49fe953195b1799f", "0100000001b8778dff640ccb144346d9db48201639b2707a0cc59e19672d2dd76cc6d1a5a6010000006b48304502210098d2e5b8a6c72442430bc09f2f4bcb56612c5b9e5eee821d65b412d099bb723402204f7f008ac052e5d7be0ab5b0c85ea5e627d725a521bd9e9b193d1fdf81c317a0012102d26e423c9da9ff4a7bf6b756b2dafb75cca34fbd34f64c4c3b77c37179c5bba2ffffffff0e0065cd1d000000001976a914ca983c2da690de3cdc693ca013d93e569810c52c88ac00ca9a3b000000001976a91477263ab93a49b1d3eb5887187704cdb82e1c60ce88ac0065cd1d000000001976a914dc40fbbc8caa1f7617d275aec9a3a14ce8d8652188ac0065cd1d000000001976a9140f2b1e5b376e22c5d1e635233eb90cf50ad9095188ac00ca9a3b000000001976a914f612ffd50b6a69df5e34ee0c5b529dfaaedca03d88ac00f633bce60000001976a914937258e3a8c463ec07e78ce62326c488170ad25e88ac0065cd1d000000001976a9142848ad5ff4cc32df3649646017c47bc04e8d211788ac00ca9a3b000000001976a914fa937737a6df2b8a5e39cce8b0bdd9510683023a88ac0065cd1d000000001976a914ae248a5d710143b6b309aaab9ce17059536e2aa388ac0065cd1d000000001976a91438d6eb11eca405be497a7183c50e437851425e0088ac00ca9a3b000000001976a91410ac6b2704146b50a1dd8f6df70736856ebf8b3488ac0065cd1d000000001976a914456816dccb3a4f33ae2dbd3ba623997075d5c73d88ac00ca9a3b000000001976a91452957c44e5bee9a60402a739fc8959c2850ea98488ac0065cd1d000000001976a914fb2dffa402d05f74335d137e21e41d8920c745fb88ac00000000"},
            {"89a72ba4732505ce9b09c30668db985952701252ce0adbd7c43336396697d6ae", "01000000011a656d67706db286d1e6fad57eb4f411cb14f8880cea8348da339b9d434a5ec7050000006a47304402201d69fddb269b53aa742ff6437a45adb4ca5c59f666c9b4eabc4a0c7a6e6f4c0f022015a747b7a6d9371a4020f5b396dcd094b0f36af3fc82e95091da856181912dfa012102c9a8d5b2f768afe30ee772d185e7a61f751be05649a79508b38a2be8824adec3ffffffff020065cd1d000000001976a914ca983c2da690de3cdc693ca013d93e569810c52c88ac00b07098e60000001976a9141630d812e219e6bcbe494eb96f7a7900c216ad5d88ac00000000"},
            {"edaf445288d8e65cf7963bc8047c90f53681acaadc5ccfc5ecc67aedbd73cddb", "010000000164a3990893c012b20287d43d1071ac26f4b93648ff4213db6da6979beed6b7dc010000006b48304502210086ac11d4a8146b4176a72059960690c72a9776468cd671fd07c064b51f24961d02205bcf008d6995014f3cfd79100ee9beab5688c88cca15c5cea38b769563785d900121036530415a7b3b9c5976f26a63a57d119ab39491762121723c773399a2531a1bd7ffffffff020065cd1d000000001976a91477263ab93a49b1d3eb5887187704cdb82e1c60ce88ac006aad74e60000001976a914e5616848352c328c9f61b167eb1b0fde39b5cb6788ac00000000"},
            {"81a1f0f8242d5e71e65ff9e8ec51e8e85d641b607d7f691c1770d4f25918ebd7", "010000000141c217dfea3a1d8d6a06e9d3daf75b292581f652256d73a7891e5dc9c7ee3cca000000006a47304402205cce451228f98fece9645052546b82c2b2d425a4889b03999001fababfc7f4690220583b2189faef07d6b0191c788301cfab1b3f47ffe2c403d632b92c6dde27e14f012102d26e423c9da9ff4a7bf6b756b2dafb75cca34fbd34f64c4c3b77c37179c5bba2ffffffff0100ca9a3b000000001976a914dc40fbbc8caa1f7617d275aec9a3a14ce8d8652188ac00000000"}
    };

    String expectedTx = "01000000039f79b1953195fe490a8036b9b1c735cb0c7efb1474700bd6e2778a3e27da74ef010000006b483045022100893b044bf2e1248d6496f41dc37acecb7da3b191df74883efa6a009f96b73ced02204e1e49c9688aff72f27d21d0639acbdff1a06a3ea9cdb2b530d91b7993af38e78121033daee143740ae505dd588be89f659b34ba30f587bcebece11d72ec7a115bc41bffffffff9f79b1953195fe490a8036b9b1c735cb0c7efb1474700bd6e2778a3e27da74ef040000006a47304402202d2197506b54d2007ce7c2b70b78774a52531c1ff85530fffd6f788305ff456e0220165bf803dc734fa6666e1aebf48eda948957fba1b86529dc4df106437f322e56812103c956c491833b8f1ebfde275cd7d5660824c53efe215f9956356b85f6c86031ffffffffffd7eb1859f2d470171c697f7d601b645de8e851ece8f95fe6715e2d24f8f0a181000000006a47304402203b0ee9b2cbfe53a8167f3cf19837832a572ab7d2144b1da38f7f8be09d8c6e5b022014d53f1b7570f6612610d35cd3d2eb58b5f61382ad9011a1d8cfd128b2645c6a81210392ed3b840c8474f8b6b57e71d9a60fbf75adea6fc68d8985330e8d782b80621fffffffff0200bbeea0000000001976a914007d5355731b44e274eb495a26f4c33a734ee3eb88ac00c2eb0b000000001976a914392d52419e94e237f0d5817de1c9e21d09b515a688ac00000000";
}
