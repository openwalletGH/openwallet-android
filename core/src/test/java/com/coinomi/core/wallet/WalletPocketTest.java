package com.coinomi.core.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.BitcoinTest;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinTest;
import com.coinomi.core.network.interfaces.TransactionEventListener;
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Sha256Hash;
import com.google.bitcoin.crypto.DeterministicHierarchy;
import com.google.bitcoin.crypto.DeterministicKey;
import com.google.bitcoin.crypto.HDKeyDerivation;
import com.google.bitcoin.store.UnreadableWalletException;
import com.google.bitcoin.utils.BriefLogFormatter;
import com.google.bitcoin.wallet.DeterministicSeed;
import com.google.bitcoin.wallet.KeyChain;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletPocketTest {
    static final List<String> MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");
    DeterministicSeed seed = new DeterministicSeed(MNEMONIC, "", 0);
    private DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());
    CoinType type = DogecoinTest.get();
    DeterministicHierarchy hierarchy = new DeterministicHierarchy(masterKey);
    DeterministicKey rootKey = hierarchy.get(type.getBip44Path(0), false, true);
    private WalletPocket pocket;

    @Before
    public void setup() {
        BriefLogFormatter.init();

        pocket = new WalletPocket(rootKey, type);
    }



    @Test
    public void watchingAddresses() {
        List<Address> addresses = pocket.getWatchingAddresses();
        assertEquals(40, addresses.size()); // 20 + 20 lookahead size
    }

    @Test
    public void fillTransactions() {
        fillDummyTransactions(pocket);
    }

    private void fillDummyTransactions(TransactionEventListener pocket) {
//        pocket.
    }

    @Test
    public void serializeUnencryptedNormal() throws UnreadableWalletException {
        serializeUnencrypted("");
    }

    public void serializeUnencrypted(String expectedSerialization) throws UnreadableWalletException {
        Protos.WalletPocket walletPocketProto = pocket.toProtobuf();

        assertEquals(expectedSerialization, walletPocketProto.toString());
    }
}
