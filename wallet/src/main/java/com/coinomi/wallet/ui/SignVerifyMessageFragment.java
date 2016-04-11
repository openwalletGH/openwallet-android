package com.coinomi.wallet.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.SignedMessage;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.tasks.SignVerifyMessageTask;
import com.coinomi.wallet.util.Keyboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static android.view.View.OnClickListener;
import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.core.Preconditions.checkState;

/**
 * Fragment that prepares a transaction
 *
 * @author John L. Jegutanis
 */
public class SignVerifyMessageFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(SignVerifyMessageFragment.class);

    private static final String PASSWORD_DIALOG_TAG = "password_dialog_tag";

    private AutoCompleteTextView signingAddressView;
    private TextView addressError;
    private EditText messageView;
    private EditText signatureView;
    private Button verifyButton;
    private Button signButton;
    private TextView signatureOK;
    private TextView signatureError;
    private WalletAccount account;
    private CoinType type;
    private WalletApplication application;
    private SignVerifyMessageTask signVerifyMessageTask;

    /**
     * Use this factory method to create a new instance of
     * this fragment using a URI.
     *
     * @param address the default address
     * @return A new instance of fragment WalletSendCoins.
     */
    public static SignVerifyMessageFragment newInstance(String address) {
        SignVerifyMessageFragment fragment = new SignVerifyMessageFragment();
        Bundle args = new Bundle();
        args.putString(Constants.ARG_ADDRESS_STRING, address);
        fragment.setArguments(args);
        return fragment;
    }



    public SignVerifyMessageFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(Constants.ARG_ACCOUNT_ID)) {
                String accountId = args.getString(Constants.ARG_ACCOUNT_ID);
                account = checkNotNull(application.getAccount(accountId));
            }
            checkNotNull(account, "No account selected");
            type = account.getCoinType();
        } else {
            throw new RuntimeException("Must provide account ID");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sign_message, container, false);

        signingAddressView = (AutoCompleteTextView) view.findViewById(R.id.signing_address);
        ArrayAdapter<AbstractAddress> adapter = new ArrayAdapter<>(getActivity(), R.layout.item_simple,
                account.getActiveAddresses());
        signingAddressView.setAdapter(adapter);

        messageView = (EditText) view.findViewById(R.id.message);
        signatureView = (EditText) view.findViewById(R.id.signature);

        addressError = (TextView) view.findViewById(R.id.address_error_message);
        signatureOK = (TextView) view.findViewById(R.id.signature_ok);
        signatureError = (TextView) view.findViewById(R.id.signature_error);

        verifyButton = (Button) view.findViewById(R.id.button_verify);
        verifyButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearFocusAndHideKeyboard();
                verifyMessage();
            }
        });

        signButton = (Button) view.findViewById(R.id.button_sign);
        signButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearFocusAndHideKeyboard();
                signMessage();
            }
        });

        return view;
    }

    private void clearFocusAndHideKeyboard() {
        signingAddressView.clearFocus();
        messageView.clearFocus();
        signatureView.clearFocus();
        Keyboard.hideKeyboard(getActivity());
    }

    private void signMessage() {
        if (account.isEncrypted()) {
            showUnlockDialog();
        } else {
            maybeStartSigningTask();
        }
    }

    private void verifyMessage() {
        maybeStartVerifyingTask();
    }

    private void showUnlockDialog() {
        Dialogs.dismissAllowingStateLoss(getFragmentManager(), PASSWORD_DIALOG_TAG);
        UnlockWalletDialog.getInstance().show(getFragmentManager(), PASSWORD_DIALOG_TAG);
    }

    private void clearMessages() {
        addressError.setVisibility(View.GONE);
        signatureOK.setVisibility(View.GONE);
        signatureError.setVisibility(View.GONE);
    }

    private void showSignVerifyStatus(SignedMessage signedMessage) {
        clearMessages();
        switch (signedMessage.getStatus()) {
            case SignedOK:
                signatureView.setText(signedMessage.getSignature());
                signatureOK.setVisibility(View.VISIBLE);
                signatureOK.setText(R.string.message_signed);
                break;
            case VerifiedOK:
                signatureOK.setVisibility(View.VISIBLE);
                signatureOK.setText(R.string.message_verified);
                break;
            default:
                showSignVerifyError(signedMessage.getStatus());
        }
    }

    private void showSignVerifyError(SignedMessage.Status status) {
        checkState(status != SignedMessage.Status.SignedOK);
        checkState(status != SignedMessage.Status.VerifiedOK);
        clearMessages();
        switch (status) {
            case AddressMalformed:
                addressError.setVisibility(View.VISIBLE);
                addressError.setText(R.string.address_error);
                break;
            case KeyIsEncrypted:
                addressError.setVisibility(View.VISIBLE);
                addressError.setText(R.string.wallet_locked_message);
                break;
            case MissingPrivateKey:
                addressError.setVisibility(View.VISIBLE);
                addressError.setText(R.string.address_not_found_error);
                break;
            case InvalidSigningAddress:
            case InvalidMessageSignature:
                signatureError.setVisibility(View.VISIBLE);
                signatureError.setText(R.string.message_verification_failed);
                break;
            case Unknown:
            default:
                signatureError.setVisibility(View.VISIBLE);
                signatureError.setText(R.string.error_generic);
        }
    }

    private void maybeStartSigningTask() {
        maybeStartSigningTask(null);
    }

    public void maybeStartSigningTask(@Nullable CharSequence password) {
        maybeStartSigningVerifyingTask(true, password);
    }

    private void maybeStartVerifyingTask() {
        maybeStartSigningVerifyingTask(false, null);
    }

    private void maybeStartSigningVerifyingTask(boolean sign, @Nullable CharSequence password) {
        if (signVerifyMessageTask == null) {
            String address = signingAddressView.getText().toString().trim();
            String message = messageView.getText().toString();
            String signature = signatureView.getText().toString();
            SignedMessage signedMessage;
            if (sign) {
                signedMessage = new SignedMessage(address, message);
            } else {
                signedMessage = new SignedMessage(address, message, signature);
            }
            signVerifyMessageTask = new SignVerifyMessageTask(account, sign, password) {
                @Override
                protected void onPostExecute(SignedMessage message) {
                    showSignVerifyStatus(message);
                    signVerifyMessageTask = null;
                }
            };
            signVerifyMessageTask.execute(signedMessage);
        }
    }

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.application = (WalletApplication) context.getApplicationContext();
    }
}
