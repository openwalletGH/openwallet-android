package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.FiatType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocketHD;
import com.coinomi.core.wallet.exceptions.Bip44KeyLookAheadExceededException;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.util.LayoutUtils;
import com.coinomi.wallet.util.Qr;
import com.coinomi.wallet.util.UiUtils;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.core.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 *
 */
public class AddressRequestFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(AddressRequestFragment.class);

    private static final int UPDATE_VIEW = 0;
    private static final int UPDATE_EXCHANGE_RATE = 1;

    // Loader IDs
    private static final int ID_RATE_LOADER = 0;

    @Nullable private Address showAddress;
    private Address receiveAddress;
    private Value amount;
    private String label;
    private String message;

    private CoinType type;
    private TextView addressLabelView;
    private TextView addressView;
    private ImageView qrView;
    private CurrencyCalculatorLink amountCalculatorLink;
    private View previousAddressesLink;

    private NavigationDrawerFragment mNavigationDrawerFragment;
    private String accountId;
    private WalletPocketHD pocket;
    private int maxQrSize;
    private String lastQrContent;

    private final Handler handler = new MyHandler(this);
    private Configuration config;
    private ContentResolver resolver;
    private LoaderManager loaderManager;

    private static class MyHandler extends WeakHandler<AddressRequestFragment> {
        public MyHandler(AddressRequestFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(AddressRequestFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    ref.updateView();
                    break;
                case UPDATE_EXCHANGE_RATE:
                    ref.amountCalculatorLink.setExchangeRate((ExchangeRate) msg.obj);
            }
        }
    }

    private final ContentObserver addressBookObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            updateView();
        }
    };

    public static AddressRequestFragment newInstance(Bundle args) {
        AddressRequestFragment fragment = new AddressRequestFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static AddressRequestFragment newInstance(String accountId) {
        return newInstance(accountId, null);
    }

    public static AddressRequestFragment newInstance(String accountId, @Nullable Address showAddress) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_ACCOUNT_ID, accountId);
        if (showAddress != null) {
            args.putSerializable(Constants.ARG_ADDRESS, showAddress);
        }
        return newInstance(args);
    }
    public AddressRequestFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();
        Bundle args = getArguments();
        if (args != null) {
            accountId = args.getString(Constants.ARG_ACCOUNT_ID);
            if (args.containsKey(Constants.ARG_ADDRESS)) {
                showAddress = (Address) args.getSerializable(Constants.ARG_ADDRESS);
            }
        }
        // TODO
        pocket = (WalletPocketHD) checkNotNull(walletApplication.getAccount(accountId));
        if (pocket == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            return;
        }
        type = pocket.getCoinType();
        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        maxQrSize = LayoutUtils.calculateMaxQrCodeSize(getResources());

        loaderManager.initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDestroy() {
        loaderManager.destroyLoader(ID_RATE_LOADER);
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_request, container, false);

        addressLabelView = (TextView) view.findViewById(R.id.request_address_label);
        addressView = (TextView) view.findViewById(R.id.request_address);
        View.OnClickListener addressOnClickListener = getAddressOnClickListener();
        addressLabelView.setOnClickListener(addressOnClickListener);
        addressView.setOnClickListener(addressOnClickListener);

        qrView = (ImageView) view.findViewById(R.id.qr_code);

        AmountEditView sendCoinAmountView = (AmountEditView) view.findViewById(R.id.send_coin_amount);
        sendCoinAmountView.setType(type);
        sendCoinAmountView.setFormat(type.getMonetaryFormat());

        AmountEditView sendLocalAmountView = (AmountEditView) view.findViewById(R.id.send_local_amount);
        sendLocalAmountView.setFormat(FiatType.FRIENDLY_FORMAT);

        amountCalculatorLink = new CurrencyCalculatorLink(sendCoinAmountView, sendLocalAmountView);

        previousAddressesLink = view.findViewById(R.id.view_previous_addresses);
        previousAddressesLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), PreviousAddressesActivity.class);
                intent.putExtra(Constants.ARG_ACCOUNT_ID, accountId);
                startActivity(intent);
            }
        });

        updateView();

        pocket.addEventListener(walletListener);

        return view;
    }

    private View.OnClickListener getAddressOnClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (showAddress != null) {
                    receiveAddress =  showAddress;
                }
                Activity activity = getActivity();
                ActionMode actionMode = UiUtils.startAddressActionMode(receiveAddress, activity,
                        getFragmentManager());
                // Hack to dismiss this action mode when back is pressed
                if (activity != null && activity instanceof WalletActivity) {
                    ((WalletActivity) activity).registerActionMode(actionMode);
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        amountCalculatorLink.setListener(amountsListener);
        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName(), type), true, addressBookObserver);

        updateView();
    }

    @Override
    public void onPause() {
        resolver.unregisterContentObserver(addressBookObserver);
        amountCalculatorLink.setListener(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        pocket.removeEventListener(walletListener);
        walletListener.removeCallbacks();
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Only show items in the action bar relevant to this screen
        // if the drawer is not showing. Otherwise, let the drawer
        // decide what to show in the action bar.
        if (mNavigationDrawerFragment == null || !mNavigationDrawerFragment.isDrawerOpen()) {
            if (showAddress == null) {
                inflater.inflate(R.menu.request, menu);
            } else {
                inflater.inflate(R.menu.request_single_address, menu);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                UiUtils.share(getActivity(), receiveAddress.toString());
                return true;
            case R.id.action_copy:
                UiUtils.copy(getActivity(), receiveAddress.toString());
                return true;
            case R.id.action_new_address:
                createNewAddressDialog.show(getFragmentManager(), null);
                return true;
            case R.id.action_edit_label:
                EditAddressBookEntryFragment.edit(getFragmentManager(), type, receiveAddress.toString());
                return true;
            default:
                // Not one of ours. Perform default menu processing
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.resolver = activity.getContentResolver();
        this.config = ((WalletApplication) activity.getApplication()).getConfiguration();
        this.loaderManager = getLoaderManager();
    }

    DialogFragment createNewAddressDialog = new DialogFragment() {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Dialog dialog;
            DialogInterface.OnClickListener dismissListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dismissAllowingStateLoss();
                }
            };

            if (pocket.canCreateFreshReceiveAddress()) {
                final LayoutInflater inflater = LayoutInflater.from(getActivity());
                final View view = inflater.inflate(R.layout.new_address_dialog, null);
                final TextView viewLabel = (TextView) view.findViewById(R.id.new_address_label);

                final DialogBuilder builder = new DialogBuilder(getActivity());
                builder.setTitle(R.string.create_new_address);
                builder.setView(view);
                builder.setNegativeButton(R.string.button_cancel, dismissListener);
                builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Address newAddress = pocket.getFreshReceiveAddress(
                                    config.isManualAddressManagement());
                            final String newLabel = viewLabel.getText().toString().trim();

                            if (!newLabel.isEmpty()) {
                                final Uri uri =
                                        AddressBookProvider.contentUri(getActivity().getPackageName(), type)
                                                .buildUpon().appendPath(newAddress.toString()).build();
                                final ContentValues values = new ContentValues();
                                values.put(AddressBookProvider.KEY_LABEL, newLabel);
                                resolver.insert(uri, values);
                            }
                            updateView();
                        } catch (Bip44KeyLookAheadExceededException e) {
                            // Should not happen as we already checked if we can create a new address
                            Toast.makeText(getActivity(), R.string.too_many_unused_addresses, Toast.LENGTH_LONG).show();
                        }
                        dismissAllowingStateLoss();
                    }
                });
                dialog = builder.create();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.too_many_unused_addresses)
                        .setPositiveButton(R.string.button_ok, dismissListener);
                dialog = builder.create();
            }
            return dialog;
        }
    };

    private void updateView() {
        if (isRemoving() || isDetached()) return;
        receiveAddress = null;
        if (showAddress != null) {
            receiveAddress =  showAddress;
        } else {
            receiveAddress = pocket.getReceiveAddress(config.isManualAddressManagement());
        }

        // Don't show previous addresses link if we are showing a specific address
        if (showAddress == null && pocket.getNumberIssuedReceiveAddresses() != 0) {
            previousAddressesLink.setVisibility(View.VISIBLE);
        } else {
            previousAddressesLink.setVisibility(View.GONE);
        }

        // TODO, get amount and description, update QR if needed

        updateLabel();

        updateQrCode(CoinURI.convertToCoinURI(receiveAddress, amount, label, message));
    }

    /**
     * Update qr code if the content is different
     * @param qrContent
     */
    private void updateQrCode(final String qrContent) {
        if (lastQrContent == null || !lastQrContent.equals(qrContent)) {
            Bitmap qrCodeBitmap = Qr.bitmap(qrContent, maxQrSize);
            qrView.setImageBitmap(qrCodeBitmap);
            lastQrContent = qrContent;
        }
    }

    private void updateLabel() {
        label = resolveLabel(receiveAddress.toString());
        if (label != null) {
            addressLabelView.setText(label);
            addressLabelView.setTypeface(Typeface.DEFAULT);
            addressView.setText(
                    GenericUtils.addressSplitToGroups(receiveAddress.toString()));
            addressView.setVisibility(View.VISIBLE);
        } else {
            addressLabelView.setText(
                    GenericUtils.addressSplitToGroupsMultiline(receiveAddress.toString()));
            addressLabelView.setTypeface(Typeface.MONOSPACE);
            addressView.setVisibility(View.GONE);
        }
    }

    private final ThrottlingWalletChangeListener walletListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            handler.sendEmptyMessage(UPDATE_VIEW);
        }
    };

    private String resolveLabel(@Nonnull final String address) {
        return AddressBookProvider.resolveLabel(getActivity(), type, address);
    }

    private final LoaderManager.LoaderCallbacks<Cursor> rateLoaderCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {
        @Override
        public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
            String localSymbol = config.getExchangeCurrencyCode();
            String coinSymbol = type.getSymbol();
            return new ExchangeRateLoader(getActivity(), config, localSymbol, coinSymbol);
        }

        @Override
        public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
            if (data != null && data.getCount() > 0) {
                data.moveToFirst();
                final ExchangeRatesProvider.ExchangeRate exchangeRate = ExchangeRatesProvider.getExchangeRate(data);
                handler.sendMessage(handler.obtainMessage(UPDATE_EXCHANGE_RATE, exchangeRate.rate));
            }
        }

        @Override
        public void onLoaderReset(final Loader<Cursor> loader) {
        }
    };

    private final AmountEditView.Listener amountsListener = new AmountEditView.Listener() {
        boolean isValid(Value amount) {
            return amount != null && amount.isPositive()
                    && amount.compareTo(type.minNonDust()) >= 0;
        }

        void checkAndUpdateAmount() {
            Value amountParsed = amountCalculatorLink.getPrimaryAmount();
            if (isValid(amountParsed)) {
                amount = amountParsed;
            } else {
                amount = null;
            }
            updateView();
        }

        @Override
        public void changed() {
            checkAndUpdateAmount();
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (!hasFocus) {
                checkAndUpdateAmount();
            }
        }
    };
}
