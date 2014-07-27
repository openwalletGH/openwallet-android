package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.bitcoin.core.Coin;
import com.google.bitcoin.core.InsufficientMoneyException;

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
    private FragmentManager fragmentManager;
    private Handler handler = new Handler();
    private EditText receivingAddressView;
    private EditText sendAmountView;
    private Button scanQrCodeButton;
    private Button sendConfirmButton;


    private State state = State.INPUT;
    private Address validatedAddress;
    private Coin sendAmount;
    private DialogFragment popupWindow;
    private Activity activity;

//    private Transaction sentTransaction = null;

    private enum State {
        INPUT, PREPARATION, SENDING, SENT, FAILED
    }

    private final class SendAmountListener implements View.OnFocusChangeListener, TextWatcher {
        @Override
        public void onFocusChange(final View v, final boolean hasFocus) {
            if (!hasFocus) {
                validateAmount(true);
            }
        }

        @Override
        public void afterTextChanged(final Editable s) {
            validateAmount(false);
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) { }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) { }
    }

    private final SendAmountListener sendAmountListener = new SendAmountListener();

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

        receivingAddressView = (EditText) view.findViewById(R.id.send_to_address);
        sendAmountView = (EditText) view.findViewById(R.id.send_amount);
        sendAmountView.setOnFocusChangeListener(sendAmountListener);
        sendAmountView.addTextChangedListener(sendAmountListener);

        scanQrCodeButton = (Button) view.findViewById(R.id.scan_qr_code);
        scanQrCodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleScan();
            }
        });

        sendConfirmButton = (Button) view.findViewById(R.id.send_confirm);
        sendConfirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                validateReceivingAddress(true);

                if (everythingValid())
                    handleSendConfirm();
                else
                    requestFocusFirst();
            }
        });
        
        return view;
    }


    private void handleScan() {
        startActivityForResult(new Intent(getActivity(), ScanActivity.class), REQUEST_CODE_SCAN);
    }

    private void handleSendConfirm() {
        state = State.PREPARATION;
        updateView();

        Toast.makeText(activity, R.string.send_coins_preparation_msg, Toast.LENGTH_LONG).show();


        try {
            application.getWallet().sendCoins(validatedAddress, sendAmount);
        } catch (InsufficientMoneyException e) {
            // TODO handle this case better
            Toast.makeText(activity, R.string.send_coins_error_msg, Toast.LENGTH_LONG).show();
        }
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

                    validatedAddress = coinUri.getAddress();
                    if (validatedAddress == null)
                        throw new CoinURIParseException("missing address");

                    updateStateFrom(validatedAddress);

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
                receivingAddressView.setText(address.toString());
                requestFocusFirst();
                updateView();
            }
        });
    }

    private void updateView() {

//        viewCancel.setEnabled(state != State.PREPARATION);
        sendConfirmButton.setEnabled(everythingValid());

        // enable actions
        if (scanQrCodeButton != null) {
            scanQrCodeButton.setEnabled(state == State.INPUT);
        }
    }

    private boolean isOutputsValid() {
        return validatedAddress != null;
    }

    private boolean isAmountValid() {
        return isAmountValid(sendAmount);
    }

    private boolean isAmountValid(Coin amount) {
        return amount != null && amount.signum() > 0;
    }

    private boolean everythingValid() {
        return state == State.INPUT && isOutputsValid() && isAmountValid();
    }

    private void requestFocusFirst()
    {
        if (!isOutputsValid())
            receivingAddressView.requestFocus();
        else if (!isAmountValid())
            sendAmountView.requestFocus();
        else if (everythingValid())
            sendConfirmButton.requestFocus();
        else
            log.warn("unclear focus");
    }

    private void validateAmount(final boolean popups) {
        try {
            Coin amount = Coin.parseCoin(String.valueOf(sendAmountView.getText()));
            if (isAmountValid(amount)) {
                sendAmount = amount;
            }
            else {
                sendAmount = null;
            }
        } catch (IllegalArgumentException ignore) {
            sendAmount = null;
        }
        updateView();
    }

    private void validateReceivingAddress(final boolean popups)
    {
        // TODO implement validation
//        try
//        {
//            final String addressStr = receivingAddressView.getText().toString().trim();
//            if (!addressStr.isEmpty())
//            {
//                final NetworkParameters addressParams = Address.getParametersFromAddress(addressStr);
//                if (addressParams != null && !addressParams.equals(Constants.NETWORK_PARAMETERS))
//                {
//                    // address is valid, but from different known network
//                    if (popups)
//                        popupMessage(receivingAddressView,
//                                getString(R.string.send_coins_fragment_receiving_address_error_cross_network, addressParams.getId()));
//                }
//                else if (addressParams == null)
//                {
//                    // address is valid, but from different unknown network
//                    if (popups)
//                        popupMessage(receivingAddressView, getString(R.string.send_coins_fragment_receiving_address_error_cross_network_unknown));
//                }
//                else
//                {
//                    // valid address
//                    final String label = AddressBookProvider.resolveLabel(activity, addressStr);
//                    validatedAddress = new AddressAndLabel(Constants.NETWORK_PARAMETERS, addressStr, label);
//                    receivingAddressView.setText(null);
//                }
//            }
//            else
//            {
//                // empty field should not raise error message
//            }
//        }
//        catch (final AddressFormatException x)
//        {
//            // could not decode address at all
//            if (popups)
//                popupMessage(receivingAddressView, getString(R.string.send_coins_fragment_receiving_address_error));
//        }
//
//        updateView();
    }

    //    // TODO: Rename method, update argument and hook method into UI event
//    public void onButtonPressed(Uri uri) {
//        if (mListener != null) {
//            mListener.onFragmentInteraction(uri);
//        }
//    }

    private void popupMessage(@Nonnull final View anchorNotUsed, @Nonnull final String message) {
        dismissPopup();

        Bundle bundle = new Bundle();
        bundle.putString(ErrorDialogFragment.MESSAGE, message);
        popupWindow = new ErrorDialogFragment();
        popupWindow.setArguments(bundle);
        popupWindow.show(fragmentManager, ErrorDialogFragment.TAG);
    }

    private void dismissPopup() {
        if (popupWindow != null) {
            popupWindow.dismiss();
            popupWindow = null;
        }
    }

    public static class ErrorDialogFragment extends DialogFragment {
        public static final String TAG = "error_dialog_fragment";
        public static final String MESSAGE = "message";
        private String message;

        public ErrorDialogFragment() {}

        @Override
        public void setArguments(Bundle args) {
            super.setArguments(args);
            message = args.getString(MESSAGE);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(message)
                    .setNeutralButton(R.string.button_dismiss, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
//                            dismissPopup();
                            dismiss();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        this.activity = activity;
        application = (WalletApplication) activity.getApplication();
        fragmentManager = getFragmentManager();
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
