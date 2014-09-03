package com.coinomi.core.wallet;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.DogecoinTest;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.protos.Protos;
import com.google.bitcoin.crypto.MnemonicException;
import com.google.bitcoin.utils.BriefLogFormatter;
import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Giannis Dzegoutanis
 */
public class WalletTest {
    static final List<String> MNEMONIC = ImmutableList.of("citizen", "fever", "scale", "nurse", "brief", "round", "ski", "fiction", "car", "fitness", "pluck", "act");
    CoinType type = DogecoinTest.get();
    private Wallet wallet;

    @Before
    public void setup() throws IOException, MnemonicException {
        BriefLogFormatter.init();

        wallet = new Wallet(MNEMONIC);

        wallet.createCoinPockets(ImmutableList.of(BitcoinMain.get(),
                LitecoinMain.get(), DogecoinMain.get()), true);
    }

    @Test
    public void serializeUnencryptedNormal() throws Exception {
        // Make the wallet generate all the needed lookahead keys
        for (WalletPocket pocket : wallet.getPockets()) {
            pocket.initializeAllKeysIfNeeded();
        }

        Protos.Wallet walletProto = wallet.toProtobuf();

        Wallet newWallet = WalletProtobufSerializer.readWallet(walletProto);

        assertEquals(walletProto.toString(), newWallet.toProtobuf().toString());
    }


}
