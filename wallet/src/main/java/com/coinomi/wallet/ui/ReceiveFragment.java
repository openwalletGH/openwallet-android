package com.coinomi.wallet.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.AddressFormater;
import com.coinomi.wallet.util.Fonts;
import com.coinomi.wallet.util.Qr;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.uri.BitcoinURI;

import java.math.BigInteger;

import javax.annotation.Nullable;

/**
 *
 */
public class ReceiveFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ReceiveFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ReceiveFragment newInstance(String param1, String param2) {
        ReceiveFragment fragment = new ReceiveFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
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
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_receive, container, false);

        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();

        TextView addressView = (TextView) view.findViewById(R.id.receive_address);
        Fonts.setTypeface(addressView, Fonts.Font.UBUNTU_MONO_REGULAR);

        Address receiveAddress = walletApplication.getWalletPocket(DogecoinMain.get()).
                getReceiveAddress();

        addressView.setText(AddressFormater.eightGroups(receiveAddress.toString()));

        ImageView qrView = (ImageView) view.findViewById(R.id.qr_code);

        Coin amount = null; //FIXME get amount from interface

        // update qr-code
        final int size = (int) (256 * getResources().getDisplayMetrics().density);
        final String qrContent = CoinURI.convertToCoinURI(receiveAddress, amount, null, null);
        Bitmap qrCodeBitmap = Qr.bitmap(qrContent, size);
        qrView.setImageBitmap(qrCodeBitmap);

        return view;
    }

    private String determineBitcoinRequestStr(final Address address, @Nullable final Coin amount) {
        final StringBuilder uri = new StringBuilder();
        return uri.toString();
    }

}
