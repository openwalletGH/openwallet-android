package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.FiatType;
import com.coinomi.core.coins.Value;
import com.coinomi.core.coins.families.BitFamily;
import com.coinomi.core.coins.families.NxtFamily;
import com.coinomi.core.exceptions.UnsupportedCoinTypeException;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.AbstractAddress;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Configuration;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.ExchangeRatesProvider;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.ui.dialogs.CreateNewAddressDialog;
import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.util.QrUtils;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.UiUtils;
import com.coinomi.wallet.util.WeakHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.coinomi.core.Preconditions.checkNotNull;
import static com.coinomi.wallet.ExchangeRatesProvider.getRate;

/**
 *
 */
public class AddressRequestFragment extends WalletFragment {
    private static final Logger log = LoggerFactory.getLogger(AddressRequestFragment.class);

    private static final int UPDATE_VIEW = 0;
    private static final int UPDATE_EXCHANGE_RATE = 1;

    // Loader IDs
    private static final int ID_RATE_LOADER = 0;

    // Fragment tags
    private static final String NEW_ADDRESS_TAG = "new_address_tag";

    private CoinType type;
    @Nullable private AbstractAddress showAddress;
    private AbstractAddress receiveAddress;
    private Value amount;
    private String label;
    private String accountId;
    private WalletAccount account;
    private String message;

    @Bind(R.id.request_address_label) TextView addressLabelView;
    @Bind(R.id.request_address) TextView addressView;
    @Bind(R.id.request_coin_amount) AmountEditView sendCoinAmountView;
    @Bind(R.id.view_previous_addresses) View previousAddressesLink;
    @Bind(R.id.qr_code) ImageView qrView;
    String lastQrContent;
    CurrencyCalculatorLink amountCalculatorLink;
    ContentResolver resolver;

    private final MyHandler handler = new MyHandler(this);
    private final ContentObserver addressBookObserver = new AddressBookObserver(handler);
    private Configuration config;

    private static class MyHandler extends WeakHandler<AddressRequestFragment> {
        public MyHandler(AddressRequestFragment ref) { super(ref); }

        @Override
        protected void weakHandleMessage(AddressRequestFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    ref.updateView();
                    break;
                case UPDATE_EXCHANGE_RATE:
                    ref.updateExchangeRate((ExchangeRate) msg.obj);
                    break;
            }
        }
    }

    static class AddressBookObserver extends ContentObserver {
        private final MyHandler handler;

        public AddressBookObserver(MyHandler handler) {
            super(handler);
            this.handler = handler;
        }

        @Override
        public void onChange(final boolean selfChange) {
            handler.sendEmptyMessage(UPDATE_VIEW);
        }
    }

    public static AddressRequestFragment newInstance(Bundle args) {
        AddressRequestFragment fragment = new AddressRequestFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static AddressRequestFragment newInstance(String accountId) {
        return newInstance(accountId, null);
    }

    public static AddressRequestFragment newInstance(String accountId,
                                                     @Nullable AbstractAddress showAddress) {
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

        // The onCreateOptionsMenu is handled in com.coinomi.wallet.ui.AccountFragment
        // or in com.coinomi.wallet.ui.PreviousAddressesActivity
        setHasOptionsMenu(true);

        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();
        Bundle args = getArguments();
        if (args != null) {
            accountId = args.getString(Constants.ARG_ACCOUNT_ID);
            if (args.containsKey(Constants.ARG_ADDRESS)) {
                showAddress = (AbstractAddress) args.getSerializable(Constants.ARG_ADDRESS);
            }
        }
        // TODO
        account = checkNotNull(walletApplication.getAccount(accountId));
        if (account == null) {
            Toast.makeText(getActivity(), R.string.no_such_pocket_error, Toast.LENGTH_LONG).show();
            return;
        }
        type = account.getCoinType();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_request, container, false);
        ButterKnife.bind(this, view);

        sendCoinAmountView.resetType(type, true);

        AmountEditView sendLocalAmountView = ButterKnife.findById(view, R.id.request_local_amount);
        sendLocalAmountView.setFormat(FiatType.FRIENDLY_FORMAT);

        amountCalculatorLink = new CurrencyCalculatorLink(sendCoinAmountView, sendLocalAmountView);

        return view;
    }

    @Override
    public void onViewStateRestored(@android.support.annotation.Nullable Bundle savedInstanceState) {
        ExchangeRatesProvider.ExchangeRate rate = getRate(getContext(), type.getSymbol(), config.getExchangeCurrencyCode());
        if (rate != null) updateExchangeRate(rate.rate);
        updateView();
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        amountCalculatorLink = null;
        lastQrContent = null;
        ButterKnife.unbind(this);
        super.onDestroyView();
    }

    @OnClick(R.id.request_address_view)
    public void onAddressClick() {
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

    @OnClick(R.id.view_previous_addresses)
    public void onPreviousAddressesClick() {
        Intent intent = new Intent(getActivity(), PreviousAddressesActivity.class);
        intent.putExtra(Constants.ARG_ACCOUNT_ID, accountId);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        account.addEventListener(walletListener);
        amountCalculatorLink.setListener(amountsListener);
        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName(), type), true, addressBookObserver);

        updateView();
    }

    @Override
    public void onPause() {
        resolver.unregisterContentObserver(addressBookObserver);
        amountCalculatorLink.setListener(null);
        account.removeEventListener(walletListener);
        walletListener.removeCallbacks();

        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_share:
                UiUtils.share(getActivity(), getUri());
                return true;
            case R.id.action_copy:
                UiUtils.copy(getActivity(), getUri());
                return true;
            case R.id.action_new_address:
                showNewAddressDialog();
                return true;
            case R.id.action_edit_label:
                EditAddressBookEntryFragment.edit(getFragmentManager(), type, receiveAddress);
                return true;
            default:
                // Not one of ours. Perform default menu processing
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onAttach(final Context  context) {
        super.onAttach(context);
        this.resolver = context.getContentResolver();
        this.config = ((WalletApplication) context.getApplicationContext()).getConfiguration();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(ID_RATE_LOADER, null, rateLoaderCallbacks);
    }

    @Override
    public void onDetach() {
        getLoaderManager().destroyLoader(ID_RATE_LOADER);
        resolver = null;
        super.onDetach();
    }

    private void showNewAddressDialog() {
        if (!isVisible() || !isResumed()) return;
        Dialogs.dismissAllowingStateLoss(getFragmentManager(), NEW_ADDRESS_TAG);
        DialogFragment dialog = CreateNewAddressDialog.getInstance(account);
        dialog.show(getFragmentManager(), NEW_ADDRESS_TAG);
    }

    private void updateExchangeRate(ExchangeRate exchangeRate) {
        amountCalculatorLink.setExchangeRate((ExchangeRate) exchangeRate);
    }

    @Override
    public void updateView() {
        if (isRemoving() || isDetached()) return;
        receiveAddress = null;
        if (showAddress != null) {
            receiveAddress =  showAddress;
        } else {
            receiveAddress = account.getReceiveAddress();
        }

        // Don't show previous addresses link if we are showing a specific address
        if (showAddress == null && account.hasUsedAddresses()) {
            previousAddressesLink.setVisibility(View.VISIBLE);
        } else {
            previousAddressesLink.setVisibility(View.GONE);
        }

        // TODO, add message

        updateLabel();

        updateQrCode(getUri());
    }

    private String getUri() {
        if (type instanceof BitFamily) {
            return CoinURI.convertToCoinURI(receiveAddress, amount, label, message);
        } else if (type instanceof NxtFamily){
            return CoinURI.convertToCoinURI(receiveAddress, amount, label, message,
                    account.getPublicKeySerialized());
        } else {
            throw new UnsupportedCoinTypeException(type);
        }
    }

    /**
     * Update qr code if the content is different
     */
    private void updateQrCode(final String qrContent) {
        if (lastQrContent == null || !lastQrContent.equals(qrContent)) {
            QrUtils.setQr(qrView, getResources(), qrContent);
            lastQrContent = qrContent;
        }
    }

    private void updateLabel() {
        label = resolveLabel(receiveAddress);
        if (label != null) {
            addressLabelView.setText(label);
            addressLabelView.setTypeface(Typeface.DEFAULT);
            addressView.setText(
                    GenericUtils.addressSplitToGroups(receiveAddress));
            addressView.setVisibility(View.VISIBLE);
        } else {
            addressLabelView.setText(
                    GenericUtils.addressSplitToGroupsMultiline(receiveAddress));
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

    private String resolveLabel(@Nonnull final AbstractAddress address) {
        return AddressBookProvider.resolveLabel(getActivity(), address);
    }

    @Override
    public WalletAccount getAccount() {
        return account;
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
                    && amount.compareTo(type.getMinNonDust()) >= 0;
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
