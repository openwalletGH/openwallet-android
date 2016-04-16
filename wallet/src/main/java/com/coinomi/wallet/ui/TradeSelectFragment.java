package com.coinomi.wallet.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.PeercoinMain;
import com.coinomi.core.coins.Value;
import com.coinomi.core.exchange.shapeshift.ShapeShift;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftCoins;
import com.coinomi.core.exchange.shapeshift.data.ShapeShiftMarketInfo;
import com.coinomi.core.util.ExchangeRate;
import com.coinomi.core.wallet.Wallet;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.tasks.AddCoinTask;
import com.coinomi.wallet.tasks.ExchangeCheckSupportedCoinsTask;
import com.coinomi.wallet.tasks.MarketInfoPollTask;
import com.coinomi.wallet.ui.adaptors.AvailableAccountsAdaptor;
import com.coinomi.wallet.ui.dialogs.ConfirmAddCoinUnlockWalletDialog;
import com.coinomi.wallet.ui.widget.AmountEditView;
import com.coinomi.wallet.util.Keyboard;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;
import com.coinomi.wallet.util.WeakHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.acra.ACRA;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import javax.annotation.Nullable;

import static com.coinomi.core.coins.Value.canCompare;

/**
 * @author John L. Jegutanis
 */
public class TradeSelectFragment extends Fragment implements ExchangeCheckSupportedCoinsTask.Listener, AddCoinTask.Listener {
    private static final Logger log = LoggerFactory.getLogger(TradeSelectFragment.class);

    private static final int UPDATE_MARKET = 0;
    private static final int UPDATE_MARKET_ERROR = 1;
    private static final int UPDATE_WALLET = 2;
    private static final int VALIDATE_AMOUNT = 3;
    private static final int INITIAL_TASK_ERROR = 4;
    private static final int UPDATE_AVAILABLE_COINS = 5;

    private static final String INITIAL_TASK_BUSY_DIALOG_TAG = "initial_task_busy_dialog_tag";
    private static final String ADD_COIN_TASK_BUSY_DIALOG_TAG = "add_coin_task_busy_dialog_tag";
    private static final String ADD_COIN_DIALOG_TAG = "add_coin_dialog_tag";

    // UI & misc
    private WalletApplication application;
    private Wallet wallet;
    private final Handler handler = new MyHandler(this);
    private final AmountListener amountsListener = new AmountListener(handler);
    private final AccountListener sourceAccountListener = new AccountListener(handler);
    @Nullable private Listener listener;
    @Nullable private MenuItem actionSwapMenu;
    private Spinner sourceSpinner;
    private Spinner destinationSpinner;
    private AvailableAccountsAdaptor sourceAdapter;
    private AvailableAccountsAdaptor destinationAdapter;
    private AmountEditView sourceAmountView;
    private AmountEditView destinationAmountView;
    private CurrencyCalculatorLink amountCalculatorLink;
    private TextView amountError;
    private TextView amountWarning;
    private Button nextButton;

    // Tasks
    private MarketInfoTask marketTask;
    private ExchangeCheckSupportedCoinsTask initialTask;
    private Timer timer;
    private MyMarketInfoPollTask pollTask;
    private AddCoinTask addCoinAndProceedTask;

    // State
    private WalletAccount sourceAccount;
    @Nullable private WalletAccount destinationAccount;
    private CoinType destinationType;
    @Nullable private Value sendAmount;
    @Nullable private Value maximumDeposit;
    @Nullable private Value minimumDeposit;
    @Nullable private Value lastBalance;
    @Nullable private ExchangeRate lastRate;


    /** Required empty public constructor */
    public TradeSelectFragment() {}


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Android callback methods

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
        setRetainInstance(true); // Retain fragment as we are running async tasks

        // Select some default coins
        sourceAccount = application.getAccount(application.getConfiguration().getLastAccountId());
        if (sourceAccount == null) {
            List<WalletAccount> accounts = application.getAllAccounts();
            sourceAccount = accounts.get(0);
        }

        // Find a destination coin that is different than the source coin
        for (CoinType type : Constants.SUPPORTED_COINS) {
            if (type.equals(sourceAccount.getCoinType())) continue;
            destinationType = type;
            break;
        }

        updateBalance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_trade_select, container, false);

        sourceSpinner = (Spinner) view.findViewById(R.id.from_coin);
        sourceSpinner.setAdapter(getSourceSpinnerAdapter());
        sourceSpinner.setOnItemSelectedListener(getSourceSpinnerListener());

        destinationSpinner = (Spinner) view.findViewById(R.id.to_coin);
        destinationSpinner.setAdapter(getDestinationSpinnerAdapter());
        destinationSpinner.setOnItemSelectedListener(getDestinationSpinnerListener());

        sourceAmountView = (AmountEditView) view.findViewById(R.id.trade_coin_amount);
        destinationAmountView = (AmountEditView) view.findViewById(R.id.receive_coin_amount);

        amountCalculatorLink = new CurrencyCalculatorLink(sourceAmountView, destinationAmountView);

//        receiveCoinWarning = (TextView) view.findViewById(R.id.warn_no_account_found);
//        receiveCoinWarning.setVisibility(View.GONE);
//        addressError = (TextView) view.findViewById(R.id.address_error_message);
//        addressError.setVisibility(View.GONE);
        amountError = (TextView) view.findViewById(R.id.amount_error_message);
        amountError.setVisibility(View.GONE);
        amountWarning = (TextView) view.findViewById(R.id.amount_warning_message);
        amountWarning.setVisibility(View.GONE);

//        scanQrCodeButton = (ImageButton) view.findViewById(R.id.scan_qr_code);
//        scanQrCodeButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                handleScan();
//            }
//        });

        view.findViewById(R.id.powered_by_shapeshift).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.about_shapeshift_title)
                        .setMessage(R.string.about_shapeshift_message)
                        .setPositiveButton(R.string.button_ok, null)
                        .create().show();
            }
        });

        nextButton = (Button) view.findViewById(R.id.button_next);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                validateAddress();
                validateAmount();
                if (everythingValid()) {
                    onHandleNext();
                } else if (amountCalculatorLink.isEmpty()) {
                    amountError.setText(R.string.amount_error_empty);
                    amountError.setVisibility(View.VISIBLE);
                }
            }
        });

        // Setup the default source & destination views
        setSource(sourceAccount, false);
        if (destinationAccount != null) {
            setDestination(destinationAccount, false);
        } else {
            setDestination(destinationType, false);
        }

        if (!application.isConnected()) {
            showInitialTaskErrorDialog(null);
        } else {
            maybeStartInitialTask();
        }

        return view;
    }

    private AvailableAccountsAdaptor getDestinationSpinnerAdapter() {
        if (destinationAdapter == null) {
            destinationAdapter = new AvailableAccountsAdaptor(getActivity());
        }
        return destinationAdapter;
    }

    private AvailableAccountsAdaptor getSourceSpinnerAdapter() {
        if (sourceAdapter == null) {
            sourceAdapter = new AvailableAccountsAdaptor(getActivity());
        }
        return sourceAdapter;
    }


    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            this.listener = (Listener) context;
            this.application = (WalletApplication) context.getApplicationContext();
            this.wallet = application.getWallet();
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.trade, menu);
        actionSwapMenu = menu.findItem(R.id.action_swap_coins);
    }

    @Override
    public void onPause() {
        stopPolling();

        removeSourceListener();

        amountCalculatorLink.setListener(null);

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        startPolling();

        amountCalculatorLink.setListener(amountsListener);

        addSourceListener();

        updateNextButtonState();
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // TODO allow to trade all coins
//            case R.id.action_empty_wallet:
//                setAmountForEmptyWallet();
//                return true;
            case R.id.action_refresh:
                refreshStartInitialTask();
                return true;
            case R.id.action_swap_coins:
                swapAccounts();
                return true;
            case R.id.action_exchange_history:
                startActivity(new Intent(getActivity(), ExchangeHistoryActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods

    private void onHandleNext() {
        if (listener != null) {
            if (destinationAccount == null) {
                createToAccountAndProceed();
            } else {
                if (everythingValid()) {
                    Keyboard.hideKeyboard(getActivity());
                    listener.onMakeTrade(sourceAccount, destinationAccount, sendAmount);
                } else {
                    Toast.makeText(getActivity(), R.string.amount_error, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void createToAccountAndProceed() {
        if (destinationType == null) {
            Toast.makeText(getActivity(), R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }

        ConfirmAddCoinUnlockWalletDialog.getInstance(destinationType, wallet.isEncrypted())
                .show(getFragmentManager(), ADD_COIN_DIALOG_TAG);
    }

    /**
     * Start account creation task and proceed
     */
    void maybeStartAddCoinAndProceedTask(@Nullable String description, @Nullable CharSequence password) {
        if (addCoinAndProceedTask == null) {
            addCoinAndProceedTask = new AddCoinTask(this, destinationType, wallet, description, password);
            addCoinAndProceedTask.execute();
        }
    }

    @Override
    public void onAddCoinTaskStarted() {
        Dialogs.ProgressDialogFragment.show(getFragmentManager(),
                getResources().getString(R.string.adding_coin_working, destinationType.getName()),
                ADD_COIN_TASK_BUSY_DIALOG_TAG);
    }

    @Override
    public void onAddCoinTaskFinished(Exception error, WalletAccount newAccount) {
        if (Dialogs.dismissAllowingStateLoss(getFragmentManager(), ADD_COIN_TASK_BUSY_DIALOG_TAG)) return;

        if (error != null) {
            if (error instanceof KeyCrypterException) {
                showPasswordRetryDialog();
            } else {
                ACRA.getErrorReporter().handleSilentException(error);
                Toast.makeText(getActivity(), R.string.error_generic, Toast.LENGTH_LONG).show();
            }
        } else {
            destinationAccount = newAccount;
            destinationType = newAccount.getCoinType();
            onHandleNext();
        }
        addCoinAndProceedTask = null;
    }

    private void addSourceListener() {
        sourceAccount.addEventListener(sourceAccountListener, Threading.SAME_THREAD);
        onWalletUpdate();
    }

    private void removeSourceListener() {
        sourceAccount.removeEventListener(sourceAccountListener);
        sourceAccountListener.removeCallbacks();
    }

    /**
     * Start polling for the market information of the current pair, if it is already stated this
     * call does nothing
     */
    private void startPolling() {
        if (timer == null) {
            ShapeShift shapeShift = application.getShapeShift();
            pollTask = new MyMarketInfoPollTask(handler, shapeShift, getPair());
            timer = new Timer();
            timer.schedule(pollTask, 0, Constants.RATE_UPDATE_FREQ_MS);
        }
    }

    /**
     * Stop the polling for the market info, if it is already stop this call does nothing
     */
    private void stopPolling() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
            pollTask.cancel();
            pollTask = null;
        }
    }

    /**
     * Updates the spinners to include only available and supported coins
     */
    private void updateAvailableCoins(ShapeShiftCoins availableCoins) {
        List<CoinType> supportedTypes = getSupportedTypes(availableCoins.availableCoinTypes);
        List<WalletAccount> allAccounts = application.getAllAccounts();

        sourceAdapter.update(allAccounts, supportedTypes, false);
        List<CoinType> sourceTypes = sourceAdapter.getTypes();

        // No supported source accounts found
        if (sourceTypes.size() == 0) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.trade_error)
                    .setMessage(R.string.trade_error_no_supported_source_accounts)
                    .setPositiveButton(R.string.button_ok, null)
                    .create().show();
            return;
        }

        if (sourceSpinner.getSelectedItemPosition() == -1) {
            if (sourceAccount != null && sourceAdapter.getAccountOrTypePosition(sourceAccount) != -1) {
                sourceSpinner.setSelection(sourceAdapter.getAccountOrTypePosition(sourceAccount));
            } else {
                sourceSpinner.setSelection(0);
            }
        }
        CoinType sourceType =
                ((AvailableAccountsAdaptor.Entry) sourceSpinner.getSelectedItem()).getType();

        // If we have only one source type, remove it as a destination
        if (sourceTypes.size() == 1) {
            ArrayList<CoinType> typesWithoutSourceType = Lists.newArrayList(supportedTypes);
            typesWithoutSourceType.remove(sourceType);
            destinationAdapter.update(allAccounts, typesWithoutSourceType, true);
        } else {
            destinationAdapter.update(allAccounts, supportedTypes, true);
        }

        if (destinationSpinner.getSelectedItemPosition() == -1) {
            for (AvailableAccountsAdaptor.Entry entry : destinationAdapter.getEntries()) {
                // Select the first item that is of a different type than the source
                if (!sourceType.equals(entry.getType())) {
                    int selectionIndex = destinationAdapter.getAccountOrTypePosition(
                            entry.accountOrCoinType);
                    destinationSpinner.setSelection(selectionIndex);
                    break;
                }
            }
        }
    }

    /**
     * Show a no connectivity error
     */
    private void showInitialTaskErrorDialog(String error) {
        if (getActivity() == null) {
            return;
        }

        DialogBuilder builder;
        if (error == null) {
            builder = DialogBuilder.warn(getActivity(), R.string.trade_warn_no_connection_title);
            builder.setMessage(R.string.trade_warn_no_connection_message);
        } else {
            builder = DialogBuilder.warn(getActivity(), R.string.trade_error);
            builder.setMessage(R.string.trade_error_service_not_available);
        }

        builder.setNegativeButton(R.string.button_dismiss, null);
        builder.setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                initialTask = null;
                maybeStartInitialTask();
            }
        });
        builder.create().show();
    }

    /**
     * Returns a list of the supported coins from the list of the available coins
     */
    private List<CoinType> getSupportedTypes(List<CoinType> availableCoins) {
        ImmutableList.Builder<CoinType> builder = ImmutableList.builder();
        for (CoinType supportedType : Constants.SUPPORTED_COINS) {
            if (availableCoins.contains(supportedType)) {
                builder.add(supportedType);
            }
        }
        return builder.build();
    }

    private void refreshStartInitialTask() {
        if (initialTask != null) {
            initialTask.cancel(true);
            initialTask = null;
        }
        maybeStartInitialTask();
    }

    private void maybeStartInitialTask() {
        if (initialTask == null) {
            initialTask = new ExchangeCheckSupportedCoinsTask(this, application);
            initialTask.execute();
        } else {

        }
    }

    @Override
    public void onExchangeCheckSupportedCoinsTaskStarted() {
        Dialogs.ProgressDialogFragment.show(getFragmentManager(),
                getString(R.string.contacting_exchange),
                INITIAL_TASK_BUSY_DIALOG_TAG);
    }

    @Override
    public void onExchangeCheckSupportedCoinsTaskFinished(Exception error, ShapeShiftCoins shapeShiftCoins) {
        if (Dialogs.dismissAllowingStateLoss(getFragmentManager(), INITIAL_TASK_BUSY_DIALOG_TAG)) return;

        if (error != null) {
            log.warn("Could not get ShapeShift coins", error);
            handler.sendMessage(handler.obtainMessage(INITIAL_TASK_ERROR, error.getMessage()));
        } else {
            if (shapeShiftCoins.isError) {
                log.warn("Could not get ShapeShift coins: {}", shapeShiftCoins.errorMessage);
                handler.sendMessage(handler.obtainMessage(INITIAL_TASK_ERROR, shapeShiftCoins.errorMessage));
            } else {
                handler.sendMessage(handler.obtainMessage(UPDATE_AVAILABLE_COINS, shapeShiftCoins));
            }
        }
    }

    /**
     * Starts a new task to query about the market of the currently selected pair.
     * Notes:
     *  - If a task is already running, this call will cancel it.
     *  - If the fragment is detached, it will not run.
     */
    private void startMarketInfoTask() {
        if (marketTask != null) {
            marketTask.cancel(true);
            marketTask = null;
        }
        if (getActivity() != null) {
            marketTask = new MarketInfoTask(handler, application.getShapeShift(), getPair());
            marketTask.execute();
        }
    }

    /**
     * Get the current source and destination pair
     */
    private String getPair() {
        return ShapeShift.getPair(sourceAccount.getCoinType(), destinationType);
    }

    /**
     * Updates the exchange rate and limits for the specific market.
     * Note: if the current pair is different that the marketInfo pair, do nothing
     */
    private void onMarketUpdate(ShapeShiftMarketInfo marketInfo) {
        // If not current pair, do nothing
        if (!marketInfo.isPair(sourceAccount.getCoinType(), destinationType)) return;

        maximumDeposit = marketInfo.limit;
        minimumDeposit = marketInfo.minimum;

        lastRate = marketInfo.rate;
        amountCalculatorLink.setExchangeRate(lastRate);
        if (amountCalculatorLink.isEmpty() && lastRate != null) {
            Value hintValue = sourceAccount.getCoinType().oneCoin();
            Value exchangedValue = lastRate.convert(hintValue);
            Value minerFee100 = marketInfo.rate.minerFee.multiply(100);
            // If hint value is too small, make it higher to get a no zero exchanged value and
            // at least 10 times higher than the miner fee
            for (int tries = 8; tries > 0 &&
                    (exchangedValue.isZero() || exchangedValue.compareTo(minerFee100) < 0); tries--) {
                hintValue = hintValue.multiply(10);
                exchangedValue = lastRate.convert(hintValue);
            }
            amountCalculatorLink.setExchangeRateHints(hintValue);
        }
    }

    /**
     * Get the item selected listener for the source spinner. It will swap the accounts if the
     * destination account is the same as the new source account.
     */
    private AdapterView.OnItemSelectedListener getSourceSpinnerListener() {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AvailableAccountsAdaptor.Entry entry =
                        (AvailableAccountsAdaptor.Entry) parent.getSelectedItem();
                if (entry.accountOrCoinType instanceof WalletAccount) {
                    WalletAccount newSource = (WalletAccount) entry.accountOrCoinType;
                    // If same account selected, do nothing
                    if (newSource.equals(sourceAccount)) return;
                    // If new source and destination are the same, swap accounts
                    if (destinationAccount != null && destinationAccount.isType(newSource)) {
                        // Swap accounts
                        setDestinationSpinner(sourceAccount);
                        setDestination(sourceAccount, false);
                    }
                    setSource(newSource, true);
                } else {
                    // Should not happen as "source" is always an account
                    throw new IllegalStateException("Unexpected class: "
                            + entry.accountOrCoinType.getClass());
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
    }

    /**
     * Get the item selected listener for the destination spinner. It will swap the accounts if the
     * source account is the same as the new destination account.
     */
    private AdapterView.OnItemSelectedListener getDestinationSpinnerListener() {
        return new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AvailableAccountsAdaptor.Entry entry =
                        (AvailableAccountsAdaptor.Entry) parent.getSelectedItem();
                if (entry.accountOrCoinType instanceof WalletAccount) {
                    WalletAccount newDestination = (WalletAccount) entry.accountOrCoinType;
                    // If same account selected, do nothing
                    if (newDestination.equals(destinationAccount)) return;
                    // If new destination and source are the same, swap accounts
                    if (destinationAccount != null && sourceAccount.isType(newDestination)) {
                        // Swap accounts
                        setSourceSpinner(destinationAccount);
                        setSource(destinationAccount, false);
                    }
                    setDestination(newDestination, true);
                } else if (entry.accountOrCoinType instanceof CoinType) {
                    setDestination((CoinType) entry.accountOrCoinType, true);
                } else {
                    // Should not happen
                    throw new IllegalStateException("Unexpected class: "
                            + entry.accountOrCoinType.getClass());
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
    }

    /**
     * Selects an account on the sourceSpinner without calling the callback. If no account found in
     * the adaptor, does not do anything
     */
    private void setSourceSpinner(WalletAccount account) {
        int newPosition = sourceAdapter.getAccountOrTypePosition(account);

        if (newPosition >= 0) {
            AdapterView.OnItemSelectedListener cb = sourceSpinner.getOnItemSelectedListener();
            sourceSpinner.setOnItemSelectedListener(null);
            sourceSpinner.setSelection(newPosition);
            sourceSpinner.setOnItemSelectedListener(cb);
        }
    }

    /**
     * Selects an account on the destinationSpinner without calling the callback. If no account
     * found in the adaptor, does not do anything
     */
    private void setDestinationSpinner(Object accountOrType) {
        int newPosition = destinationAdapter.getAccountOrTypePosition(accountOrType);

        if (newPosition >= 0) {
            AdapterView.OnItemSelectedListener cb = destinationSpinner.getOnItemSelectedListener();
            destinationSpinner.setOnItemSelectedListener(null);
            destinationSpinner.setSelection(newPosition);
            destinationSpinner.setOnItemSelectedListener(cb);
        }
    }

    /**
     * Sets the source account and makes a network call to ask about the new pair.
     * Note: this does not update the source spinner, use {@link #setSourceSpinner(WalletAccount)}
     */
    private void setSource(WalletAccount account, boolean startNetworkTask) {
        removeSourceListener();
        sourceAccount = account;
        addSourceListener();

        sourceAmountView.reset();
        sourceAmountView.setType(sourceAccount.getCoinType());
        sourceAmountView.setFormat(sourceAccount.getCoinType().getMonetaryFormat());

        amountCalculatorLink.setExchangeRate(null);

        minimumDeposit = null;
        maximumDeposit = null;

        updateOptionsMenu();

        if (startNetworkTask) {
            startMarketInfoTask();
            if (pollTask != null) pollTask.updatePair(getPair());
            application.maybeConnectAccount(sourceAccount);
        }
    }

    /**
     * Sets the destination account and makes a network call to ask about the new pair.
     * Note: this does not update the destination spinner, use
     * {@link #setDestinationSpinner(Object)}
     */
    private void setDestination(WalletAccount account, boolean startNetworkTask) {
        setDestination(account.getCoinType(), false);
        destinationAccount = account;
        updateOptionsMenu();

        if (startNetworkTask) {
            startMarketInfoTask();
            if (pollTask != null) pollTask.updatePair(getPair());
        }
    }

    /**
     * Sets the destination coin type and makes a network call to ask about the new pair.
     * Note: this does not update the destination spinner, use
     * {@link #setDestinationSpinner(Object)}
     */
    private void setDestination(CoinType type, boolean startNetworkTask) {
        destinationAccount = null;
        destinationType = type;

        destinationAmountView.reset();
        destinationAmountView.setType(destinationType);
        destinationAmountView.setFormat(destinationType.getMonetaryFormat());

        amountCalculatorLink.setExchangeRate(null);

        minimumDeposit = null;
        maximumDeposit = null;

        updateOptionsMenu();

        if (startNetworkTask) {
            startMarketInfoTask();
            if (pollTask != null) pollTask.updatePair(getPair());
        }
    }

    /**
     * Swap the source & destination accounts.
     * Note: this works if the destination is an account, not a CoinType.
     */
    private void swapAccounts() {
        if (isSwapAccountPossible()) {
            WalletAccount newSource = destinationAccount;
            WalletAccount newDestination = sourceAccount;

            setSourceSpinner(newSource);
            setDestinationSpinner(newDestination);

            setSource(newSource, false);
            setDestination(newDestination, true);
        } else {
            // Should not happen as we need to first check if isSwapAccountPossible() before showing
            // a swap action to the user
            Toast.makeText(getActivity(), R.string.error_generic,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check is if possible to perform the {@link #swapAccounts()} action
     */
    private boolean isSwapAccountPossible() {
        return destinationAccount != null;
    }

    /**
     * Updates the options menu to take in to account the new selected accounts types, i.e. disable
     * the swap action
     */
    private void updateOptionsMenu() {
        if (actionSwapMenu != null) {
            actionSwapMenu.setEnabled(isSwapAccountPossible());
        }
    }

    private void updateBalance() {
        lastBalance = sourceAccount.getBalance();
    }

    private void onWalletUpdate() {
        updateBalance();
        validateAmount();
    }

    /**
     * Check if amount is within the minimum and maximum deposit limits and if is dust or if is more
     * money than currently in the wallet
     */
    private boolean isAmountWithinLimits(Value amount) {
        boolean isWithinLimits = !amount.isDust();

        // Check if within min & max deposit limits
        if (isWithinLimits && minimumDeposit != null && maximumDeposit != null &&
                minimumDeposit.isOfType(amount) && maximumDeposit.isOfType(amount)) {
            isWithinLimits = amount.within(minimumDeposit, maximumDeposit);
        }

        // Check if we have the amount
        if (isWithinLimits && canCompare(lastBalance, amount)) {
            isWithinLimits = amount.compareTo(lastBalance) <= 0;
        }

        return isWithinLimits;
    }

    /**
     * Check if amount is smaller than the dust limit or if applicable, the minimum deposit.
     */
    private boolean isAmountTooSmall(Value amount) {
        return amount.compareTo(getLowestAmount(amount)) < 0;
    }

    /**
     * Get the lowest deposit or withdraw for the provided amount type
     */
    private Value getLowestAmount(Value amount) {
        Value min = amount.type.getMinNonDust();
        if (minimumDeposit != null) {
            if (minimumDeposit.isOfType(min)) {
                min = Value.max(minimumDeposit, min);
            } else if (lastRate != null && lastRate.canConvert(amount.type, minimumDeposit.type)) {
                min = Value.max(lastRate.convert(minimumDeposit), min);
            }
        }
        return min;
    }

    /**
     * Check if the amount is valid
     */
    private boolean isAmountValid(Value amount) {
        boolean isValid = amount != null && !amount.isDust();

        if (isValid && amount.isOfType(sourceAccount.getCoinType())) {
            isValid = isAmountWithinLimits(amount);
        }

        return isValid;
    }

    /**
     * {@inheritDoc #validateAmount(boolean)}
     */
    private void validateAmount() {
        validateAmount(false);
    }

    /**
     * Validate amount and show errors if needed
     */
    private void validateAmount(boolean isTyping) {
        Value depositAmount = amountCalculatorLink.getPrimaryAmount();
        Value withdrawAmount = amountCalculatorLink.getSecondaryAmount();
        Value requestedAmount = amountCalculatorLink.getRequestedAmount();

        if (isAmountValid(depositAmount) && isAmountValid(withdrawAmount)) {
            sendAmount = requestedAmount;
            amountError.setVisibility(View.GONE);
            // Show warning that fees apply when entered the full amount inside the pocket
            if (canCompare(lastBalance, depositAmount) && lastBalance.compareTo(depositAmount) == 0) {
                amountWarning.setText(R.string.amount_warn_fees_apply);
                amountWarning.setVisibility(View.VISIBLE);
            } else {
                amountWarning.setVisibility(View.GONE);
            }
        } else {
            amountWarning.setVisibility(View.GONE);
            sendAmount = null;
            boolean showErrors = shouldShowErrors(isTyping, depositAmount) ||
                                 shouldShowErrors(isTyping, withdrawAmount);
            // ignore printing errors for null and zero amounts
            if (showErrors) {
                if (depositAmount == null || withdrawAmount == null) {
                    amountError.setText(R.string.amount_error);
                } else if (depositAmount.isNegative() || withdrawAmount.isNegative()) {
                    amountError.setText(R.string.amount_error_negative);
                } else if (!isAmountWithinLimits(depositAmount) || !isAmountWithinLimits(withdrawAmount)) {
                    String message = getString(R.string.error_generic);
                    // If the amount is dust or lower than the deposit limit
                    if (isAmountTooSmall(depositAmount) || isAmountTooSmall(withdrawAmount)) {
                        Value minimumDeposit = getLowestAmount(depositAmount);
                        Value minimumWithdraw = getLowestAmount(withdrawAmount);
                        message = getString(R.string.trade_error_min_limit,
                                minimumDeposit.toFriendlyString() + " (" + minimumWithdraw
                                        .toFriendlyString() + ")");
                    } else {
                        // If we have the amount
                        if (canCompare(lastBalance, depositAmount) &&
                                depositAmount.compareTo(lastBalance) > 0) {
                            message = getString(R.string.amount_error_not_enough_money,
                                    lastBalance.toFriendlyString());
                        }

                        if (canCompare(maximumDeposit, depositAmount) &&
                                depositAmount.compareTo(maximumDeposit) > 0) {

                            String maxDepositString = maximumDeposit.toFriendlyString();
                            if (lastRate != null &&
                                    lastRate.canConvert(maximumDeposit.type, destinationType)) {
                                maxDepositString += " (" + lastRate.convert(maximumDeposit)
                                        .toFriendlyString() + ")";
                            }
                            message = getString(R.string.trade_error_max_limit, maxDepositString);
                        }
                    }
                    amountError.setText(message);
                } else { // Should not happen, but show a generic error
                    amountError.setText(R.string.amount_error);
                }
                amountError.setVisibility(View.VISIBLE);
            } else {
                amountError.setVisibility(View.GONE);
            }
        }
        updateNextButtonState();
    }

    // TODO implement
    private boolean isOutputsValid() {
        return true;
    }


    private boolean everythingValid() {
        return isOutputsValid() && isAmountValid(sendAmount);
    }

    private void updateNextButtonState() {
//        nextButton.setEnabled(everythingValid());
    }

    /**
     * Decide if should show errors in the UI.
     */
    private boolean shouldShowErrors(boolean isTyping, Value amountParsed) {
        if (canCompare(amountParsed, lastBalance) && amountParsed.compareTo(lastBalance) >= 0) {
            return true;
        }

        if (isTyping) return false;
        if (amountCalculatorLink.isEmpty()) return false;
        if (amountParsed != null && amountParsed.isZero()) return false;

        return true;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Public classes and interfaces

    public interface Listener {
        void onMakeTrade(WalletAccount fromAccount, WalletAccount toAccount, Value amount);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Private classes


    private static class AmountListener implements AmountEditView.Listener {
        private final Handler handler;

        private AmountListener(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void changed() {
            handler.sendMessage(handler.obtainMessage(VALIDATE_AMOUNT, true));
        }

        @Override
        public void focusChanged(final boolean hasFocus) {
            if (!hasFocus) {
                handler.sendMessage(handler.obtainMessage(VALIDATE_AMOUNT, false));
            }
        }
    }

    private static class AccountListener extends ThrottlingWalletChangeListener {
        private final Handler handler;

        private AccountListener(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void onThrottledWalletChanged() {
            handler.sendEmptyMessage(UPDATE_WALLET);
        }
    }

    /**
     * The fragment handler
     */
    private static class MyHandler extends WeakHandler<TradeSelectFragment> {
        public MyHandler(TradeSelectFragment referencingObject) { super(referencingObject); }

        @Override
        protected void weakHandleMessage(TradeSelectFragment ref, Message msg) {
            switch (msg.what) {
                case UPDATE_MARKET:
                    ref.onMarketUpdate((ShapeShiftMarketInfo) msg.obj);
                    break;
                case UPDATE_MARKET_ERROR:
                    String errorMessage = ref.getString(R.string.trade_error_market_info,
                            ref.sourceAccount.getCoinType().getName(),
                            ref.destinationType.getName());
                    Toast.makeText(ref.getActivity(), errorMessage, Toast.LENGTH_LONG).show();
                    break;
                case UPDATE_WALLET:
                    ref.onWalletUpdate();
                    break;
                case VALIDATE_AMOUNT:
                    ref.validateAmount((Boolean) msg.obj);
                    break;
                case INITIAL_TASK_ERROR:
                    ref.showInitialTaskErrorDialog((String) msg.obj);
                    break;
                case UPDATE_AVAILABLE_COINS:
                    ref.updateAvailableCoins((ShapeShiftCoins) msg.obj);
                    ref.startMarketInfoTask();
                    break;
            }
        }
    }

    /**
     * Task to query about the market of a particular pair
     */
    private static class MarketInfoTask extends AsyncTask<Void, Void, ShapeShiftMarketInfo> {
        final ShapeShift shapeShift;
        final String pair;
        final Handler handler;

        private MarketInfoTask(Handler handler, ShapeShift shift, String pair) {
            this.shapeShift = shift;
            this.handler = handler;
            this.pair = pair;
        }

        @Override
        protected ShapeShiftMarketInfo doInBackground(Void... params) {
            return MarketInfoPollTask.getMarketInfoSync(shapeShift, pair);
        }

        @Override
        protected void onPostExecute(ShapeShiftMarketInfo marketInfo) {
            if (marketInfo != null) {
                handler.sendMessage(handler.obtainMessage(UPDATE_MARKET, marketInfo));
            } else {
                handler.sendEmptyMessage(UPDATE_MARKET_ERROR);
            }
        }
    }

    private static class MyMarketInfoPollTask extends MarketInfoPollTask {
        private final Handler handler;

        MyMarketInfoPollTask(Handler handler, ShapeShift shapeShift, String pair) {
            super(shapeShift, pair);
            this.handler = handler;
        }

        @Override
        public void onHandleMarketInfo(ShapeShiftMarketInfo marketInfo) {
            handler.sendMessage(handler.obtainMessage(UPDATE_MARKET, marketInfo));
        }
    }

    private void showPasswordRetryDialog() {
        DialogBuilder.warn(getActivity(), R.string.unlocking_wallet_error_title)
                .setMessage(R.string.unlocking_wallet_error_detail)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createToAccountAndProceed();
                    }
                })
                .create().show();
    }
}