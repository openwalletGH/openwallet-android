package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.service.CoinService;
import com.coinomi.wallet.service.CoinServiceImpl;
import com.coinomi.wallet.tasks.CheckUpdateTask;
import com.coinomi.wallet.util.Keyboard;
import com.coinomi.wallet.util.SystemUtils;
import com.coinomi.wallet.util.UiUtils;
import com.coinomi.wallet.util.WeakHandler;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import static com.coinomi.wallet.ui.NavDrawerItemType.ITEM_COIN;
import static com.coinomi.wallet.ui.NavDrawerItemType.ITEM_SECTION_TITLE;
import static com.coinomi.wallet.ui.NavDrawerItemType.ITEM_TRADE;


/**
 * @author John L. Jegutanis
 * @author Andreas Schildbach
 */
final public class WalletActivity extends BaseWalletActivity implements
        NavigationDrawerFragment.NavigationDrawerCallbacks, BalanceFragment.Listener,
        SendFragment.Listener {
    private static final Logger log = LoggerFactory.getLogger(WalletActivity.class);

    private static final int RECEIVE = 0;
    private static final int BALANCE = 1;
    private static final int SEND = 2;

    private static final int REQUEST_CODE_SCAN = 0;
    private static final int ADD_COIN = 1;

    private static final int TX_BROADCAST_OK = 0;
    private static final int TX_BROADCAST_ERROR = 1;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    /**
     * For SharedPreferences, used to check if first launch ever.
     */
    private ViewPager mViewPager;
    private AppSectionsPagerAdapter pagerAdapter;
    private String currentAccountId;
    private Intent connectCoinIntent;
    private List<NavDrawerItem> navDrawerItems = new ArrayList<>();
    private ActionMode lastActionMode;
    private final Handler handler = new MyHandler(this);

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

        if (getIntent().getBooleanExtra(Constants.ARG_TEST_WALLET, false)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.test_wallet)
                    .setMessage(R.string.test_wallet_message)
                    .setPositiveButton(R.string.button_ok, null)
                    .create().show();
        }

        if (savedInstanceState == null) {
            checkAlerts();
        }

        mTitle = getTitle();

//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
        getSupportActionBar().setElevation(0);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        // Set up the drawer.
        createNavDrawerItems();
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout),
                navDrawerItems);

        // Set up the ViewPager, attaching the adapter and setting up a listener for when the
        // user swipes between sections.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        // Set OffscreenPageLimit to 2 because receive fragment draws a QR code and we don't
        // want to re-render that if we go to the SendFragment and back
        mViewPager.setOffscreenPageLimit(2);
        mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override public void onPageScrolled(int pos, float posOffset, int posOffsetPixels) { }

            @Override
            public void onPageSelected(int position) {
                if (position == BALANCE) Keyboard.hideKeyboard(WalletActivity.this);
                finishActionMode();
            }

            @Override public void onPageScrollStateChanged(int state) {}
        });

        // Get the last used wallet pocket and select it
        WalletAccount lastAccount = getAccount(getWalletApplication().getConfiguration().getLastAccountId());
        if (lastAccount == null && getAllAccounts().size() > 0) {
            lastAccount = getAllAccounts().get(0);
        }
        // TODO, when we are able to remove accounts, show a message when no accounts found
        if (lastAccount != null) {
            navDrawerSelectAccount(lastAccount, false);
            openPocket(lastAccount, false);
        }
    }

    private void navDrawerSelectAccount(@Nullable WalletAccount account, boolean closeDrawer) {
        if (mNavigationDrawerFragment != null && account != null) {
            int position = 0;
            for (NavDrawerItem item : navDrawerItems) {
                if (item.itemType == ITEM_COIN && account.getId().equals(item.itemData)) {
                    mNavigationDrawerFragment.setSelectedItem(position, closeDrawer);
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
        for (WalletAccount account : getAllAccounts()) {
            CoinType type = account.getCoinType();
            NavDrawerItem.addItem(navDrawerItems, ITEM_COIN, type.getName(), Constants.COINS_ICONS.get(type), account.getId());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        getWalletApplication().startBlockchainService(CoinService.ServiceMode.CANCEL_COINS_RECEIVED);
        connectCoinService();
        //TODO
//        checkLowStorageAlert();
    }


    @Override
    public void onLocalAmountClick() {
        startExchangeRates();
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

        openPocket(getAccount(accountId), false);
    }

    @Override
    public void onAddCoinsSelected() {
        startActivityForResult(new Intent(WalletActivity.this, AddCoinsActivity.class), ADD_COIN);
    }

    @Override
    public void onTradeSelected() {
        startActivity(new Intent(WalletActivity.this, TradeActivity.class));
        // Reselect the current account as the trade is a separate activity
        navDrawerSelectAccount(getAccount(currentAccountId), true);
    }

    private void openPocket(WalletAccount account) {
        openPocket(account, true);
    }

    private void openPocket(String accountId) {
        openPocket(getAccount(accountId), true);
    }

    private void openPocket(WalletAccount account, boolean selectInNavDrawer) {
        if (account != null && mViewPager != null && !account.getId().equals(currentAccountId)) {
            currentAccountId = account.getId();
            CoinType type = account.getCoinType();
            mTitle = type.getName();
            pagerAdapter = new AppSectionsPagerAdapter(this, account);
            mViewPager.setAdapter(pagerAdapter);
            mViewPager.setCurrentItem(BALANCE);
            mViewPager.getAdapter().notifyDataSetChanged();
            getWalletApplication().getConfiguration().touchLastAccountId(currentAccountId);
            connectCoinService();
            if (selectInNavDrawer) {
                navDrawerSelectAccount(account, true);
            }
        }
    }

    private void connectCoinService() {
        if (connectCoinIntent == null) {
            connectCoinIntent = new Intent(CoinService.ACTION_CONNECT_COIN, null,
                    getWalletApplication(), CoinServiceImpl.class);
        }
        // Open connection if needed or possible
        connectCoinIntent.putExtra(Constants.ARG_ACCOUNT_ID, currentAccountId);
        getWalletApplication().startService(connectCoinIntent);
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
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
                        openPocket(accountId);
                    }
                }
            }
        });
    }

    private void showScanFailedMessage(Exception e) {
        String error = getResources().getString(R.string.scan_error, e.getMessage());
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    private void processInput(String input) throws CoinURIParseException, AddressFormatException {
        input = input.trim();
        try {
            processUri(input);
        } catch (final CoinURIParseException x) {
            processAddress(input);
        }
    }

    private void processUri(String input) throws CoinURIParseException {
        CoinURI coinUri = new CoinURI(input);
        CoinType scannedType = coinUri.getTypeRequired();

        if (!Constants.SUPPORTED_COINS.contains(scannedType)) {
            String error = getResources().getString(R.string.unsupported_coin, scannedType.getName());
            throw new CoinURIParseException(error);
        }

        WalletAccount selectedAccount = null;
        List<WalletAccount> allAccounts = getAllAccounts();
        List<WalletAccount> sendFromAccounts = getAccounts(scannedType);
        if (sendFromAccounts.size() == 1) {
            selectedAccount = sendFromAccounts.get(0);
        } else if (allAccounts.size() == 1) {
            selectedAccount = allAccounts.get(0);
        }

        if (coinUri.isAddressRequest() && selectedAccount != null) {
            UiUtils.replyAddressRequest(this, coinUri, selectedAccount);
            return;
        }

        if (selectedAccount != null) {
            setSendFromCoin(selectedAccount, coinUri);
        } else {
            showPayWithDialog(coinUri);
        }

    }

    private void processAddress(String addressStr) throws AddressFormatException, CoinURIParseException {
        List<CoinType> possibleTypes = GenericUtils.getPossibleTypes(addressStr);
        WalletAccount currentAccount = getAccount(currentAccountId);

        if (currentAccount != null && possibleTypes.contains(currentAccount.getCoinType())) {
            Address address = new Address(currentAccount.getCoinType(), addressStr);
            processUri(CoinURI.convertToCoinURI(address, null, null, null));
        } else if (possibleTypes.size() == 1) {
            Address address = new Address(possibleTypes.get(0), addressStr);
            processUri(CoinURI.convertToCoinURI(address, null, null, null));
        } else {
            // This address string could be more that one coin type so first check if this address
            // comes from an account to determine the type.
            List<WalletAccount> possibleAccounts = getAccounts(possibleTypes);
            Address addressOfAccount = null;
            for (WalletAccount account : possibleAccounts) {
                Address testAddress = new Address(account.getCoinType(), addressStr);
                if (account.isAddressMine(testAddress)) {
                    addressOfAccount = testAddress;
                    break;
                }
            }
            if (addressOfAccount != null) {
                // If address is from an account don't show a dialog.
                processUri(CoinURI.convertToCoinURI(addressOfAccount, null, null, null));
            } else {
                // As a last resort let the use choose the correct coin type
                showPayToDialog(addressStr);
            }
        }
    }

    private void showPayToDialog(String addressStr) {
        if (selectCoinTypeDialog.getArguments() == null) {
            selectCoinTypeDialog.setArguments(new Bundle());
        }
        selectCoinTypeDialog.getArguments().putString(Constants.ARG_ADDRESS_STRING, addressStr);
        selectCoinTypeDialog.show(getSupportFragmentManager(), null);
    }

    SelectCoinTypeDialog selectCoinTypeDialog = new SelectCoinTypeDialog() {
        // FIXME crash when this dialog being restored from saved state
        @Override
        public void onAddressSelected(Address selectedAddress) {
            try {
                processUri(CoinURI.convertToCoinURI(selectedAddress, null, null, null));
            } catch (CoinURIParseException e) {
                showScanFailedMessage(e);
            }
        }
    };

    private void showPayWithDialog(CoinURI uri) {
        if (payWithDialog.getArguments() == null) {
            payWithDialog.setArguments(new Bundle());
        }
        payWithDialog.getArguments().putString(Constants.ARG_URI, uri.toUriString());
        payWithDialog.show(getSupportFragmentManager(), null);
    }

    PayWithDialog payWithDialog = new PayWithDialog() {
        // FIXME crash when this dialog being restored from saved state
        @Override
        public void payWith(WalletAccount account, CoinURI uri) {
            setSendFromCoin(account, uri);
        }
    };

    private void setSendFromCoin(final WalletAccount account, final CoinURI coinUri) {
        if (mViewPager != null && mNavigationDrawerFragment != null) {
            openPocket(account);
            mViewPager.setCurrentItem(SEND);

            try {
                ((SendFragment) pagerAdapter.getItem(SEND)).updateStateFrom(coinUri);
            } catch (CoinURIParseException e) {
                showScanFailedMessage(e);
            }
        } else {
            // Should not happen
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mNavigationDrawerFragment != null && !mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
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
        } else if (id == R.id.action_about) {
            startActivity(new Intent(WalletActivity.this, AboutActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void startExchangeRates() {
        WalletAccount account = getAccount(currentAccountId);
        if (account != null) {
            Intent intent = new Intent(this, ExchangeRatesActivity.class);
            intent.putExtra(Constants.ARG_COIN_ID, account.getCoinType().getId());
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_wallet_pocket_selected, Toast.LENGTH_LONG).show();
        }
    }

    void signVerifyMessage() {
        if (isAccountExists(currentAccountId)) {
            Intent intent = new Intent(this, SignVerifyMessageActivity.class);
            intent.putExtra(Constants.ARG_ACCOUNT_ID, currentAccountId);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_wallet_pocket_selected, Toast.LENGTH_LONG).show();
        }
    }

    void accountDetails() {
        if (isAccountExists(currentAccountId)) {
            Intent intent = new Intent(this, AccountDetailsActivity.class);
            intent.putExtra(Constants.ARG_ACCOUNT_ID, currentAccountId);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_wallet_pocket_selected, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshWallet() {
        if (getWalletApplication().getWallet() != null) {
            Intent intent = new Intent(CoinService.ACTION_RESET_ACCOUNT, null,
                    getWalletApplication(), CoinServiceImpl.class);
            intent.putExtra(Constants.ARG_ACCOUNT_ID, currentAccountId);
            getWalletApplication().startService(intent);
            // FIXME, we get a crash if the activity is not restarted
            Intent introIntent = new Intent(WalletActivity.this, WalletActivity.class);
            startActivity(introIntent);
            finish();
        }
    }

    private void startIntro() {
        Intent introIntent = new Intent(this, IntroActivity.class);
        startActivity(introIntent);
    }

    @Override
    public void onBackPressed() {
        if (mNavigationDrawerFragment != null && mNavigationDrawerFragment.isDrawerOpen()) {
            mNavigationDrawerFragment.closeDrawer();
            return;
        }

        // If not in balance screen, back button brings us there
        boolean screenChanged = goToBalance();
        if (!screenChanged) {
            super.onBackPressed();
        }
        finishActionMode();
    }

    private boolean goToBalance() {
        if (mViewPager != null && mViewPager.getCurrentItem() != BALANCE) {
            mViewPager.setCurrentItem(BALANCE);
            return true;
        }
        return false;
    }

    private boolean resetSend() {
        if (pagerAdapter != null) {
            ((SendFragment) pagerAdapter.getItem(SEND)).reset();
            return true;
        }
        return false;
    }

    private boolean goToSend() {
        if (mViewPager != null && mViewPager.getCurrentItem() != SEND) {
            mViewPager.setCurrentItem(SEND);
            return true;
        }
        return false;
    }

    public void registerActionMode(ActionMode actionMode) {
        finishActionMode();
        lastActionMode = actionMode;
    }

    private void finishActionMode() {
        if (lastActionMode != null) {
            lastActionMode.finish();
            lastActionMode = null;
        }
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
            }
        }
    }

    private static class AppSectionsPagerAdapter extends FragmentStatePagerAdapter {

        private final WalletActivity walletActivity;
        private final String accountId;
        private AddressRequestFragment request;
        private SendFragment send;
        private BalanceFragment balance;

        public AppSectionsPagerAdapter(WalletActivity walletActivity, WalletAccount account) {
            super(walletActivity.getSupportFragmentManager());
            this.walletActivity = walletActivity;
            this.accountId = account.getId();
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case RECEIVE:
                    if (request == null) request = AddressRequestFragment.newInstance(accountId);
                    return request;
                case SEND:
                    if (send == null) send = SendFragment.newInstance(accountId);
                    return send;
                case BALANCE:
                default:
                    if (balance == null) balance = BalanceFragment.newInstance(accountId);
                    return balance;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case RECEIVE:
                    return walletActivity.getString(R.string.wallet_title_request);
                case SEND:
                    return walletActivity.getString(R.string.wallet_title_send);
                case BALANCE:
                default:
                    return walletActivity.getString(R.string.wallet_title_balance);
            }
        }
    }
}
