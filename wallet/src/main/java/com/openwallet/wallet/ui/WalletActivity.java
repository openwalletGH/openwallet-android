package com.openwallet.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.openwallet.core.coins.CoinType;
import com.openwallet.core.exceptions.AddressMalformedException;
import com.openwallet.core.uri.CoinURI;
import com.openwallet.core.uri.CoinURIParseException;
import com.openwallet.core.util.GenericUtils;
import com.openwallet.core.wallet.AbstractAddress;
import com.openwallet.core.wallet.SerializedKey;
import com.openwallet.core.wallet.WalletAccount;
import com.openwallet.wallet.Constants;
import com.openwallet.wallet.R;
import com.openwallet.wallet.service.CoinService;
import com.openwallet.wallet.service.CoinServiceImpl;
import com.openwallet.wallet.tasks.CheckUpdateTask;
import com.openwallet.wallet.ui.dialogs.TermsOfUseDialog;
import com.openwallet.wallet.util.SystemUtils;
import com.openwallet.wallet.util.WeakHandler;

import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import static com.openwallet.wallet.ui.NavDrawerItemType.ITEM_COIN;
import static com.openwallet.wallet.ui.NavDrawerItemType.ITEM_OVERVIEW;
import static com.openwallet.wallet.ui.NavDrawerItemType.ITEM_SECTION_TITLE;
import static com.openwallet.wallet.ui.NavDrawerItemType.ITEM_TRADE;


/**
 * @author John L. Jegutanis
 * @author Andreas Schildbach
 */
final public class WalletActivity extends BaseWalletActivity implements
        NavigationDrawerFragment.Listener,
        AccountFragment.Listener, OverviewFragment.Listener, SelectCoinTypeDialog.Listener,
        PayWithDialog.Listener, TermsOfUseDialog.Listener {
    private static final Logger log = LoggerFactory.getLogger(WalletActivity.class);


    private static final int REQUEST_CODE_SCAN = 0;
    private static final int ADD_COIN = 1;

    private static final int TX_BROADCAST_OK = 0;
    private static final int TX_BROADCAST_ERROR = 1;
    private static final int SET_URI = 2;
    private static final int OPEN_ACCOUNT = 3;
    private static final int OPEN_OVERVIEW = 4;
    private static final int PROCESS_URI = 5;

    // Fragment tags
    private static final String ACCOUNT_TAG = "account_tag";
    private static final String OVERVIEW_TAG = "overview_tag";
    private static final String PAY_TO_DIALOG_TAG = "pay_to_dialog_tag";
    private static final String PAY_WITH_DIALOG_TAG = "pay_with_dialog_tag";
    private static final String TERMS_OF_USE_TAG = "terms_of_use_tag";

    // Saved state variables
    private static final String OVERVIEW_VISIBLE = "overview_visible";

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence title;

    @Nullable private String lastAccountId;
    private Intent connectCoinIntent;
    private Intent connectAllCoinIntent;
    private List<NavDrawerItem> navDrawerItems = new ArrayList<>();
    private ActionMode lastActionMode;
    private final Handler handler = new MyHandler(this);
    private boolean isOverviewVisible;
    private OverviewFragment overviewFragment;
    @Nullable private AccountFragment accountFragment;

    public WalletActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        if (getWalletApplication().getWallet() == null) {
            startIntro();
            finish();
            return;
        }

        if (savedInstanceState == null && !getConfiguration().getTermsAccepted()) {
            TermsOfUseDialog.newInstance().show(getFM(), TERMS_OF_USE_TAG);
        }

        lastAccountId = getWalletApplication().getConfiguration().getLastAccountId();

        // Create the overview and account fragments
        FragmentTransaction tr = getFM().beginTransaction();
        if (savedInstanceState == null) {
            checkAlerts();

            // Add overview fragment
            overviewFragment = OverviewFragment.getInstance();
            tr.add(R.id.contents, overviewFragment, OVERVIEW_TAG).hide(overviewFragment);

            // When we have more than one account, show overview as default
            List<WalletAccount> accounts = getAllAccounts();
            if (accounts.size() > 1) {
                handler.sendMessage(handler.obtainMessage(OPEN_OVERVIEW));
            } else if (accounts.size() == 1) {
                handler.sendMessage(handler.obtainMessage(OPEN_ACCOUNT, accounts.get(0)));
            }
            // Else no accounts, how to handle this case? TODO
        } else {
            isOverviewVisible = savedInstanceState.getBoolean(OVERVIEW_VISIBLE);
            overviewFragment = (OverviewFragment) getFM().findFragmentByTag(OVERVIEW_TAG);
            accountFragment = (AccountFragment) getFM().findFragmentByTag(ACCOUNT_TAG);

            if (isOverviewVisible || accountFragment == null) {
                tr.show(overviewFragment);
                if (accountFragment != null) tr.hide(accountFragment);
                setOverviewTitle();
            } else {
                tr.show(accountFragment).hide(overviewFragment);
                setAccountTitle(accountFragment.getAccount());
            }
        }
        tr.commit();

        // Setup navigation bar
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFM().findFragmentById(R.id.navigation_drawer);
        // Set up the drawer.
        createNavDrawerItems();
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout),
                navDrawerItems);

        if (getIntent().hasExtra(Constants.ARG_URI)) {
            handler.sendMessage(handler.obtainMessage(PROCESS_URI,
                    getIntent().getStringExtra(Constants.ARG_URI)));
            getIntent().removeExtra(Constants.ARG_URI);
        }
    }

    private void setOverviewTitle() {
        title = getResources().getString(R.string.title_activity_overview);
    }

    private void setAccountTitle(@Nullable WalletAccount account) {
        if (account != null) {
            title = account.getDescriptionOrCoinName();
        } else {
            title = "";
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(OVERVIEW_VISIBLE, isOverviewVisible);
    }

    private void navDrawerSelectAccount(@Nullable WalletAccount account, boolean closeDrawer) {
        if (mNavigationDrawerFragment != null && account != null) {
            int position = 0;
            for (NavDrawerItem item : navDrawerItems) {
                if (item.itemType == ITEM_COIN && account.getId().equals(item.itemData)) {
                    mNavigationDrawerFragment.setSelectedItem(position, closeDrawer);
                    break;
                }
                position++;
            }
        }
    }

    private void navDrawerSelectOverview(boolean closeDrawer) {
        if (mNavigationDrawerFragment != null) {
            int position = 0;
            for (NavDrawerItem item : navDrawerItems) {
                if (item.itemType == ITEM_OVERVIEW) {
                    mNavigationDrawerFragment.setSelectedItem(position, closeDrawer);
                    break;
                }
                position++;
            }
        }
    }

    private void createNavDrawerItems() {
        navDrawerItems.clear();
        NavDrawerItem.addItem(navDrawerItems, ITEM_SECTION_TITLE, getString(R.string.navigation_drawer_services));
        NavDrawerItem.addItem(navDrawerItems, ITEM_TRADE, getString(R.string.title_activity_trade), R.drawable.trade, null);
        NavDrawerItem.addItem(navDrawerItems, ITEM_SECTION_TITLE, getString(R.string.navigation_drawer_wallet));
        NavDrawerItem.addItem(navDrawerItems, ITEM_OVERVIEW, getString(R.string.title_activity_overview), R.drawable.ic_launcher, null);
        for (WalletAccount account : getAllAccounts()) {
            NavDrawerItem.addItem(navDrawerItems, ITEM_COIN, account.getDescriptionOrCoinName(),
                    Constants.COINS_ICONS.get(account.getCoinType()), account.getId());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWalletApplication().startBlockchainService(CoinService.ServiceMode.CANCEL_COINS_RECEIVED);
        connectAllCoinService();

        // Restore the correct action bar shadow
        if (getSupportActionBar() != null) {
            if (isOverviewVisible) {
                getSupportActionBar().setElevation(
                        getResources().getDimensionPixelSize(R.dimen.active_elevation));
            } else {
                getSupportActionBar().setElevation(0);
            }
        }
    }


    @Override
    public void onLocalAmountClick() {
        startExchangeRates();
    }

    @Override
    public void onRefresh() {
        refreshWallet();
    }

    @Override
    public void onTransactionBroadcastSuccess(WalletAccount pocket, Transaction transaction) {
        handler.sendMessage(handler.obtainMessage(TX_BROADCAST_OK, transaction));
    }

    @Override
    public void onTransactionBroadcastFailure(WalletAccount pocket, Transaction transaction) {
        handler.sendMessage(handler.obtainMessage(TX_BROADCAST_ERROR, transaction));
    }

    @Override
    public void onAccountSelected(String accountId) {
        log.info("Coin selected {}", accountId);

        openAccount(accountId);
    }

    @Override
    public void onAddCoinsSelected() {
        startActivityForResult(new Intent(WalletActivity.this, AddCoinsActivity.class), ADD_COIN);
    }

    @Override
    public void onTradeSelected() {
        startActivity(new Intent(WalletActivity.this, TradeActivity.class));
        // Reselect the last item as the trade is a separate activity
        if (isOverviewVisible) {
            navDrawerSelectOverview(true);
        } else {
            navDrawerSelectAccount(getAccount(lastAccountId), true);
        }
    }

    @Override
    public void onOverviewSelected() {
        openOverview(false);
    }

    public void openOverview() {
        openOverview(true);
    }

    public void openOverview(boolean selectInNavDrawer) {
        if (!isOverviewVisible && !isFinishing()) {
            setOverviewTitle();
            FragmentTransaction ft = getFM().beginTransaction();
            ft.show(overviewFragment);
            if (accountFragment != null) ft.hide(accountFragment);
            ft.commit();
            isOverviewVisible = true;
            connectAllCoinService();
            if (selectInNavDrawer) {
                navDrawerSelectOverview(true);
            }
            // Restore the default action bar shadow
            if (getSupportActionBar() != null) {
                getSupportActionBar().setElevation(
                        getResources().getDimensionPixelSize(R.dimen.active_elevation));
            }
        }
    }

    private void openAccount(WalletAccount account) {
        openAccount(account, true);
    }

    private void openAccount(String accountId) {
        openAccount(getAccount(accountId), true);
    }

    private void openAccount(WalletAccount account, boolean selectInNavDrawer) {
        if (account != null && !isFinishing()) {
            if (isAccountVisible(account)) return;

            FragmentTransaction ft = getFM().beginTransaction();
            ft.hide(overviewFragment);

            // If this account fragment is hidden, show it
            if (accountFragment != null && account.getId().equals(lastAccountId)) {
                ft.show(accountFragment);
            } else {
                // Else create a new fragment for the new account
                lastAccountId = account.getId();
                if (accountFragment != null) ft.remove(accountFragment);
                accountFragment = AccountFragment.getInstance(lastAccountId);
                ft.add(R.id.contents, accountFragment, ACCOUNT_TAG);
                getWalletApplication().getConfiguration().touchLastAccountId(lastAccountId);
            }
            ft.commit();

            setAccountTitle(account);
            isOverviewVisible = false;

            connectCoinService(lastAccountId);
            if (selectInNavDrawer) {
                navDrawerSelectAccount(account, true);
            }
            // Hide the shadow of the action bar because the PagerTabStrip of the AccountFragment is visible
            if (getSupportActionBar() != null) {
                getSupportActionBar().setElevation(0);
            }
        }
    }

    private boolean isAccountVisible(WalletAccount account) {
        return account != null && accountFragment != null &&
                accountFragment.isVisible() && account.equals(accountFragment.getAccount());
    }

    private void connectCoinService(String accountId) {
        if (connectCoinIntent == null) {
            connectCoinIntent = new Intent(CoinService.ACTION_CONNECT_COIN, null,
                    getWalletApplication(), CoinServiceImpl.class);
        }
        // Open connection if needed or possible
        connectCoinIntent.putExtra(Constants.ARG_ACCOUNT_ID, accountId);
        getWalletApplication().startService(connectCoinIntent);
    }

    private void connectAllCoinService() {
        if (connectAllCoinIntent == null) {
            connectAllCoinIntent = new Intent(CoinService.ACTION_CONNECT_ALL_COIN, null,
                    getWalletApplication(), CoinServiceImpl.class);
        }
        getWalletApplication().startService(connectAllCoinIntent);
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(title);
        }
    }

    private void checkAlerts() {
        // If not store version, show update dialog if needed
        if (!SystemUtils.isStoreVersion(this)) {
            final PackageInfo packageInfo = getWalletApplication().packageInfo();
            new CheckUpdateTask() {
                @Override
                protected void onPostExecute(Integer serverVersionCode) {
                    if (serverVersionCode != null && serverVersionCode > packageInfo.versionCode) {
                        showUpdateDialog();
                    }
                }
            }.execute();
        }
    }

    private void showUpdateDialog() {

        final PackageManager pm = getPackageManager();
//        final Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(Constants.MARKET_APP_URL, getPackageName())));
        final Intent binaryIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.BINARY_URL));

        final AlertDialog.Builder builder = new AlertDialog.Builder(WalletActivity.this);
        builder.setTitle(R.string.wallet_update_title);
        builder.setMessage(R.string.wallet_update_message);

        // Disable market link for now
//        if (pm.resolveActivity(marketIntent, 0) != null)
//        {
//            builder.setPositiveButton("Play Store", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(final DialogInterface dialog, final int id) {
//                    startActivity(marketIntent);
//                    finish();
//                }
//            });
//        }

        if (pm.resolveActivity(binaryIntent, 0) != null)
        {
            builder.setPositiveButton(R.string.button_download, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int id) {
                    startActivity(binaryIntent);
                    finish();
                }
            });
        }

        builder.setNegativeButton(R.string.button_dismiss, null);
        builder.create().show();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (requestCode == REQUEST_CODE_SCAN) {
                    if (resultCode == Activity.RESULT_OK) {
                        try {
                            processInput(intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT));
                        } catch (final Exception e) {
                            showScanFailedMessage(e);
                        }
                    }
                } else if (requestCode == ADD_COIN) {
                    if (resultCode == Activity.RESULT_OK) {
                        final String accountId = intent.getStringExtra(Constants.ARG_ACCOUNT_ID);
                        createNavDrawerItems();
                        mNavigationDrawerFragment.setItems(navDrawerItems);
                        openAccount(accountId);
                    }
                }
            }
        });
    }

    private void showScanFailedMessage(Exception e) {
        String error = getResources().getString(R.string.scan_error, e.getMessage());
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    private void processInput(String input) throws CoinURIParseException, AddressMalformedException {
        input = input.trim();
        try {
            processUri(input);
        } catch (final CoinURIParseException x) {
            if (SerializedKey.isSerializedKey(input)) {
                sweepWallet(input);
            } else {
                processAddress(input);
            }
        }
    }

    private void processUri(String input) throws CoinURIParseException {
        CoinURI coinUri = new CoinURI(input);
        CoinType scannedType = coinUri.getTypeRequired();

        if (!Constants.SUPPORTED_COINS.contains(scannedType)) {
            String error = getResources().getString(R.string.unsupported_coin, scannedType.getName());
            throw new CoinURIParseException(error);
        }

        if (accountFragment != null && accountFragment.isVisible() && accountFragment.getAccount() != null) {
            payWith(accountFragment.getAccount(), coinUri);
            return;
        }

        WalletAccount selectedAccount = null;
        List<WalletAccount> allAccounts = getAllAccounts();
        List<WalletAccount> sendFromAccounts = getAccounts(scannedType);
        if (sendFromAccounts.size() == 1) {
            selectedAccount = sendFromAccounts.get(0);
        } else if (allAccounts.size() == 1) {
            selectedAccount = allAccounts.get(0);
        }

        // TODO rework the address request standard
//        if (coinUri.isAddressRequest() && selectedAccount != null) {
//            UiUtils.replyAddressRequest(this, coinUri, selectedAccount);
//            return;
//        }

        if (selectedAccount != null) {
            payWith(selectedAccount, coinUri);
        } else {
            showPayWithDialog(coinUri);
        }
    }

    private void processAddress(String addressStr) throws CoinURIParseException, AddressMalformedException {
        List<CoinType> possibleTypes = GenericUtils.getPossibleTypes(addressStr);

        if (possibleTypes.size() == 1) {
            AbstractAddress address = possibleTypes.get(0).newAddress(addressStr);
            processUri(CoinURI.convertToCoinURI(address, null, null, null));
        } else {
            // This address string could be more that one coin type so first check if this address
            // comes from an account to determine the type.
            List<WalletAccount> possibleAccounts = getAccounts(possibleTypes);
            AbstractAddress addressOfAccount = null;
            for (WalletAccount account : possibleAccounts) {
                AbstractAddress testAddress = account.getCoinType().newAddress(addressStr);
                if (account.isAddressMine(testAddress)) {
                    addressOfAccount = testAddress;
                    break;
                }
            }
            if (addressOfAccount != null){
                // If address is from an account don't show a dialog.
                processUri(CoinURI.convertToCoinURI(addressOfAccount, null, null, null));
            } else {
                // As a last resort let the use choose the correct coin type
                showPayToDialog(addressStr);
            }
        }
    }

    public void showPayToDialog(String addressStr) {
        Dialogs.dismissAllowingStateLoss(getFM(), PAY_TO_DIALOG_TAG);
        SelectCoinTypeDialog.getInstance(addressStr).show(getFM(), PAY_TO_DIALOG_TAG);
    }

    @Override
    public void onAddressTypeSelected(AbstractAddress selectedAddress) {
        try {
            processUri(CoinURI.convertToCoinURI(selectedAddress, null, null, null));
        } catch (CoinURIParseException e) {
            showScanFailedMessage(e);
        }
    }

    private void showPayWithDialog(CoinURI uri) {
        Dialogs.dismissAllowingStateLoss(getFM(), PAY_WITH_DIALOG_TAG);
        PayWithDialog.getInstance(uri).show(getFM(), PAY_WITH_DIALOG_TAG);
    }

    @Override
    public void payWith(final WalletAccount account, final CoinURI coinUri) {
        openAccount(account);
        // Set url asynchronously as the account may need to open
        handler.sendMessage(handler.obtainMessage(SET_URI, coinUri));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Only show items in the action bar relevant to this screen
        // if the drawer is not showing. Otherwise, let the drawer
        // decide what to show in the action bar.
        if (mNavigationDrawerFragment != null && !mNavigationDrawerFragment.isDrawerOpen()) {
            getMenuInflater().inflate(R.menu.global, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(WalletActivity.this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_scan_qr_code) {
            startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
            return true;
        } else if (id == R.id.action_refresh_wallet) {
            refreshWallet();
            return true;
        } else if (id == R.id.action_sign_verify_message) {
            signVerifyMessage();
            return true;
        } else if (id == R.id.action_account_details) {
            accountDetails();
            return true;
        } else if (id == R.id.action_sweep_wallet) {
            sweepWallet(null);
            return true;
        } else if (id == R.id.action_support) {
            sendSupportEmail();
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(WalletActivity.this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void sendSupportEmail() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // only email apps should handle this
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{Constants.SUPPORT_EMAIL});
        intent.putExtra(Intent.EXTRA_SUBJECT, "");
        try {
            startActivity(Intent.createChooser(intent,
                    getResources().getString(R.string.support_message)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    void startExchangeRates() {
        WalletAccount account = getAccount(lastAccountId);
        if (account != null) {
            Intent intent = new Intent(this, ExchangeRatesActivity.class);
            intent.putExtra(Constants.ARG_COIN_ID, account.getCoinType().getId());
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_wallet_pocket_selected, Toast.LENGTH_LONG).show();
        }
    }

    void signVerifyMessage() {
        if (isAccountExists(lastAccountId)) {
            Intent intent = new Intent(this, SignVerifyMessageActivity.class);
            intent.putExtra(Constants.ARG_ACCOUNT_ID, lastAccountId);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_wallet_pocket_selected, Toast.LENGTH_LONG).show();
        }
    }

    void accountDetails() {
        if (isAccountExists(lastAccountId)) {
            Intent intent = new Intent(this, AccountDetailsActivity.class);
            intent.putExtra(Constants.ARG_ACCOUNT_ID, lastAccountId);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_wallet_pocket_selected, Toast.LENGTH_LONG).show();
        }
    }

    void sweepWallet(@Nullable String key) {
        if (isAccountExists(lastAccountId)) {
            Intent intent = new Intent(this, SweepWalletActivity.class);
            intent.putExtra(Constants.ARG_ACCOUNT_ID, lastAccountId);
            if (key != null) intent.putExtra(Constants.ARG_PRIVATE_KEY, key);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_wallet_pocket_selected, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshWallet() {
        if (getWalletApplication().getWallet() != null) {
            Intent intent;
            if (isOverviewVisible) {
                intent = new Intent(CoinService.ACTION_RESET_WALLET, null,
                        getWalletApplication(), CoinServiceImpl.class);
            } else {
                intent = new Intent(CoinService.ACTION_RESET_ACCOUNT, null,
                        getWalletApplication(), CoinServiceImpl.class);
                intent.putExtra(Constants.ARG_ACCOUNT_ID, lastAccountId);
            }
            getWalletApplication().startService(intent);
        }
    }

    private void startIntro() {
        Intent introIntent = new Intent(this, IntroActivity.class);
        startActivity(introIntent);
    }

    @Override
    public void onBackPressed() {
        finishActionMode();
        if (mNavigationDrawerFragment != null && mNavigationDrawerFragment.isDrawerOpen()) {
            mNavigationDrawerFragment.closeDrawer();
            return;
        }

        List<WalletAccount> accounts = getAllAccounts();

        if (accounts.size() > 1) {
            if (isOverviewVisible) {
                super.onBackPressed();
            } else {
                // If not in balance screen, back button brings us there
                boolean screenChanged = goToBalance();
                if (!screenChanged) {
                    // When in balance screen, it brings us to the overview
                    openOverview();
                }
            }
        } else if (accounts.size() == 1) {
            if (accountFragment != null && accountFragment.isVisible()) {
                // If not in balance screen, back button brings us there
                boolean screenChanged = goToBalance();
                if (!screenChanged) {
                    // When in balance screen, exit
                    super.onBackPressed();
                }
            } else {
                openAccount(accounts.get(0));
            }
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("unused")
    private boolean goToReceive() {
        return accountFragment != null && accountFragment.isVisible() && accountFragment.goToReceive(true);
    }

    private boolean goToBalance() {
        return accountFragment != null && accountFragment.isVisible() && accountFragment.goToBalance(true);
    }

    private boolean goToSend() {
        return accountFragment != null && accountFragment.isVisible() && accountFragment.goToSend(true);
    }

    private boolean resetSend() {
        return accountFragment != null && accountFragment.isVisible() && accountFragment.resetSend();
    }

    @Override
    public void registerActionMode(ActionMode actionMode) {
        finishActionMode();
        lastActionMode = actionMode;
    }

    @Override
    public void onReceiveSelected() {
        finishActionMode();
    }

    @Override
    public void onBalanceSelected() {
        finishActionMode();
    }

    @Override
    public void onSendSelected() {
        finishActionMode();
    }


    private void finishActionMode() {
        if (lastActionMode != null) {
            lastActionMode.finish();
            lastActionMode = null;
        }
    }

    @Override
    public void onAccountModified(WalletAccount account) {
        // Recreate items
        createNavDrawerItems();
        mNavigationDrawerFragment.setItems(navDrawerItems);
    }

    @Override
    public void onTermsAgree() {
        getConfiguration().setTermAccepted(true);
    }

    @Override
    public void onTermsDisagree() {
        getConfiguration().setTermAccepted(false);
        finish();
    }

    private static class MyHandler extends WeakHandler<WalletActivity> {
        public MyHandler(WalletActivity ref) { super(ref); }

        @Override
        protected void weakHandleMessage(WalletActivity ref, Message msg) {
            switch (msg.what) {
                case TX_BROADCAST_OK:
                    Toast.makeText(ref, ref.getString(R.string.sent_msg),
                            Toast.LENGTH_LONG).show();
                    ref.goToBalance();
                    ref.resetSend();
                    break;
                case TX_BROADCAST_ERROR:
                    Toast.makeText(ref, ref.getString(R.string.get_tx_broadcast_error),
                            Toast.LENGTH_LONG).show();
                    ref.goToSend();
                    break;
                case SET_URI:
                    if (ref.accountFragment == null) {
                        Toast.makeText(ref, ref.getString(R.string.no_wallet_pocket_selected),
                                Toast.LENGTH_LONG).show();
                    }
                    ref.accountFragment.sendToUri((CoinURI) msg.obj);
                    break;
                case OPEN_ACCOUNT:
                    ref.openAccount((WalletAccount) msg.obj);
                    break;
                case OPEN_OVERVIEW:
                    ref.openOverview();
                    break;
                case PROCESS_URI:
                    try {
                        ref.processUri((String) msg.obj);
                    } catch (CoinURIParseException e) {
                        ref.showScanFailedMessage(e);
                    }
                    break;
            }
        }
    }
}
