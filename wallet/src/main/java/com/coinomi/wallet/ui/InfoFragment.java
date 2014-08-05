package com.coinomi.wallet.ui;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.other.Eur;
import com.coinomi.core.coins.other.Usd;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.Amount;
import com.google.bitcoin.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Use the {@link InfoFragment#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class InfoFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(InfoFragment.class);

    private static final String COIN_TYPE = "coin_type";

    @Nullable private WalletApplication application;
    private CoinType type;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param type of the coin
     * @return A new instance of fragment InfoFragment.
     */

    public static InfoFragment newInstance(CoinType type) {
        InfoFragment fragment = new InfoFragment();
        Bundle args = new Bundle();
        args.putSerializable(COIN_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    public InfoFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = (CoinType) getArguments().getSerializable(COIN_TYPE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_info, container, false);

        Amount mainAmount = (Amount) view.findViewById(R.id.main_amount);
        mainAmount.setAmount(Coin.valueOf(18200000, 50));
        mainAmount.setSymbol(type.getSymbol());

        Amount btcAmount = (Amount) view.findViewById(R.id.amount_btc);
        btcAmount.setAmount(Coin.valueOf(673400000));
        btcAmount.setSymbol(BitcoinMain.get().getSymbol());

        Amount usdAmount = (Amount) view.findViewById(R.id.amount_usd);
        usdAmount.setAmount(Coin.valueOf(3985, 80));
        usdAmount.setSymbol(Usd.get().getSymbol());

        Amount eurAmount = (Amount) view.findViewById(R.id.amount_eur);
        eurAmount.setAmount(Coin.valueOf(2867, 20));
        eurAmount.setSymbol(Eur.get().getSymbol());

        return view;
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        application = (WalletApplication) activity.getApplication();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        application = null;
    }
}
