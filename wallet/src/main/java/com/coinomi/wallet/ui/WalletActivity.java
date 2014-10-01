package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
final public class WalletActivity extends ActionBarActivity implements
        NavigationDrawerFragment.NavigationDrawerCallbacks {
    private static final Logger log = LoggerFactory.getLogger(WalletActivity.class);

    private static final int RECEIVE = 0;
    private static final int INFO = 1;
    private static final int SEND = 2;
    private static final int MENU_BITCOIN = 0;
    private static final int MENU_DOGECOIN = 1;
    private static final int MENU_LITECOIN = 2;

    private static final int REQUEST_CODE_SCAN = 0;

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
    private AsyncTask<Void, Void, Void> refreshTask;
    private CoinType currentType;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        if (getWalletApplication().getWallet() == null) {
            startIntro();
            finish();
        }

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        // Set up the ViewPager, attaching the adapter and setting up a listener for when the
        // user swipes between sections.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        // Set OffscreenPageLimit to 2 because receive fragment draws a QR code and we don't
        // want to re-render that if we go to the SendFragment and back
        mViewPager.setOffscreenPageLimit(2);

        // Hack to make the ViewPager select the InfoFragment
        mNavigationDrawerFragment.reselectLastItem();
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        getWalletApplication().startBlockchainService(true);

        //TODO
//        checkLowStorageAlert();
    }

    protected WalletApplication getWalletApplication() {
        return (WalletApplication) getApplication();
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        CoinType selectedType = DogecoinMain.get();
        if (position == MENU_BITCOIN) {
            selectedType = BitcoinMain.get();
        } else if (position == MENU_DOGECOIN) {
            selectedType = DogecoinMain.get();
        } else if (position == MENU_LITECOIN) {
            selectedType = LitecoinMain.get();
        }

        log.info("Coin selected {} {}", position, selectedType);

        if (mViewPager != null && !selectedType.equals(currentType)) {
            AppSectionsPagerAdapter adapter = new AppSectionsPagerAdapter(this, selectedType);
            mViewPager.setAdapter(adapter);
            mViewPager.setCurrentItem(INFO);
            mViewPager.getAdapter().notifyDataSetChanged();
            currentType = selectedType;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.wallet, menu);
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
            return true;
        } else if (id == R.id.action_restore_wallet) {
            startRestore();
            finish();
            return true;
        } else if (id == R.id.scan_qr_code_menu) {
            startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
            return true;
        } else if (id == R.id.action_refresh_wallet) {
            refreshWallet();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

//    @Override
//    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
//        if (requestCode == REQUEST_CODE_SCAN) {
//            if (resultCode == Activity.RESULT_OK) {
//                final String input = intent.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
//
//                try {
//                    final CoinURI coinUri = new CoinURI(input);
//
//                    Address address = coinUri.getAddress();
//                    if (address == null) {
//                        throw new CoinURIParseException("missing address");
//                    }
//                    Coin amount = coinUri.getAmount();
//                    String label = coinUri.getLabel();
//
//                    // TODO start the correct fragment
//                    Toast.makeText(this, "Amount "+amount.toPlainString()+", addr "+address, Toast.LENGTH_LONG).show();
//                } catch (final CoinURIParseException x) {
//                    log.info("got invalid uri: '" + input + "'", x);
//                }
//            }
//        }
//    }

    private void refreshWallet() {
        if (refreshTask == null) {
            refreshTask = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if (getWalletApplication().getWallet() != null) {
                        getWalletApplication().getWallet().refresh();
                    }
                    return null;
                }
                @Override
                protected void onPostExecute(Void aVoid) {
                    refreshTask = null;
                    Intent introIntent = new Intent(WalletActivity.this, WalletActivity.class);
                    startActivity(introIntent);
                    finish();
                }
            };
            refreshTask.execute();
        }
    }

    private void startIntro() {
        Intent introIntent = new Intent(this, IntroActivity.class);
        startActivity(introIntent);
    }

    private void startRestore() {
        startActivity(new Intent(this, IntroActivity.class));
    }

    private static class AppSectionsPagerAdapter extends FragmentStatePagerAdapter {

        private final CoinType type;
        private final WalletActivity walletActivity;

        public AppSectionsPagerAdapter(WalletActivity walletActivity, CoinType type) {
            super(walletActivity.getSupportFragmentManager());
            this.walletActivity = walletActivity;
            this.type = type;
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case RECEIVE:
                    return RequestFragment.newInstance(type);
                case SEND:
                    return SendFragment.newInstance(type);
                case INFO:
                default:
                    return InfoFragment.newInstance(type);
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
                case INFO:
                default:
                    return walletActivity.getString(R.string.wallet_title_info);
            }
        }
    }

}
