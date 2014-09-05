package com.coinomi.wallet.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.AddressFormater;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.Qr;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;

import javax.annotation.Nullable;

/**
 *
 */
public class ReceiveFragment extends Fragment {
    private static final String COIN_TYPE = "coin_type";

    private CoinType coinType;
    private TextView addressView;
    private WalletApplication walletApplication;
    private ImageView qrView;

    private String address;
    private Coin amount;
    private String label;

    public static ReceiveFragment newInstance(CoinType coinType) {
        ReceiveFragment fragment = new ReceiveFragment();
        Bundle args = new Bundle();
        args.putSerializable(COIN_TYPE, coinType);
        fragment.setArguments(args);
        return fragment;
    }
    public ReceiveFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            coinType = (CoinType) getArguments().getSerializable(COIN_TYPE);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_receive, container, false);

        walletApplication = (WalletApplication) getActivity().getApplication();
        addressView = (TextView) view.findViewById(R.id.receive_address);
        Fonts.setTypeface(addressView, Fonts.Font.UBUNTU_MONO_REGULAR);
        qrView = (ImageView) view.findViewById(R.id.qr_code);

        updateView();

        return view;
    }

    private void updateView() {
        Address receiveAddress = walletApplication.getWalletPocket(coinType).getReceiveAddress();

        // TODO, get amount and description, update QR if needed

        addressView.setText(AddressFormater.eightGroups(receiveAddress.toString()));
        // update qr-code
        final int size = (int) (256 * getResources().getDisplayMetrics().density);
        final String qrContent = CoinURI.convertToCoinURI(receiveAddress, amount, label, null);
        Bitmap qrCodeBitmap = Qr.bitmap(qrContent, size);
        qrView.setImageBitmap(qrCodeBitmap);
    }
}
