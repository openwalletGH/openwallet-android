package com.coinomi.wallet.ui;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.uri.BitcoinURI;
import com.google.bitcoin.uri.BitcoinURIParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link WalletSendCoins.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link WalletSendCoins#newInstance} factory method to
 * create an instance of this fragment.
 *
 */
public class WalletSendCoins extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(WalletSendCoins.class);

    // the fragment initialization parameters
    private static final String COIN_TYPE = "coin_type";
    private static final int REQUEST_CODE_SCAN = 0;

    private CoinType coinType;
    private WalletApplication application;
    private Handler handler = new Handler();
    private Button scanQrCode;
    private EditText sendToAddress;


//    private OnFragmentInteractionListener mListener;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param type the type of the coin
     * @return A new instance of fragment WalletSendCoins.
     */
    public static WalletSendCoins newInstance(CoinType type) {
        WalletSendCoins fragment = new WalletSendCoins();
        Bundle args = new Bundle();
        args.putSerializable(COIN_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }
    public WalletSendCoins() {
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
        View view = inflater.inflate(R.layout.fragment_wallet_send_coins, container, false);

        scanQrCode = (Button) view.findViewById(R.id.scan_qr_code);
        scanQrCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleScan();
            }
        });
        sendToAddress = (EditText) view.findViewById(R.id.send_to_address);
        
        return view;
    }


    private void handleScan() {
        startActivityForResult(new Intent(getActivity(), ScanActivity.class), REQUEST_CODE_SCAN);
    }


    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_CODE_SCAN) {
            if (resultCode == Activity.RESULT_OK) {
                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);


                // Todo use StringInputParser and PaymentIntent
                try
                {
                    final CoinURI coinUri = new CoinURI(coinType, input);

                    final Address address = coinUri.getAddress();
                    if (address == null)
                        throw new CoinURIParseException("missing address");

                    updateStateFrom(address);

//                    if (address.getParameters().equals(Constants.NETWORK_PARAMETERS))
//                        handlePaymentIntent(PaymentIntent.fromBitcoinUri(bitcoinUri));
//                    else
//                        error(R.string.input_parser_invalid_address, input);
                }
                catch (final CoinURIParseException x)
                {
                    log.info("got invalid bitcoin uri: '" + input + "'", x);
                }

//                new StringInputParser(input)
//                {
//                    @Override
//                    protected void handlePaymentIntent(final PaymentIntent paymentIntent)
//                    {
//                        updateStateFrom(paymentIntent);
//                    }
//
//                    @Override
//                    protected void handleDirectTransaction(final Transaction transaction)
//                    {
//                        cannotClassify(input);
//                    }
//
//                    @Override
//                    protected void error(final int messageResId, final Object... messageArgs)
//                    {
//                        dialog(activity, null, R.string.button_scan, messageResId, messageArgs);
//                    }
//                }.parse();
            }
        }
    }


    private void updateStateFrom(final @Nonnull Address address) {
        log.info("got {}", address);

        // delay these actions until fragment is resumed
        handler.post(new Runnable()
        {
            @Override
            public void run() {
                WalletSendCoins.this.sendToAddress.setText(address.toString());
            }
        });
    }


    //    // TODO: Rename method, update argument and hook method into UI event
//    public void onButtonPressed(Uri uri) {
//        if (mListener != null) {
//            mListener.onFragmentInteraction(uri);
//        }
//    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        application = (WalletApplication) activity.getApplication();
//        try {
//            mListener = (OnFragmentInteractionListener) activity;
//        } catch (ClassCastException e) {
//            throw new ClassCastException(activity.toString()
//                    + " must implement OnFragmentInteractionListener");
//        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
//        mListener = null;
        application = null;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        public void onFragmentInteraction(Uri uri);
    }

}
