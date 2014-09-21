package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.coinomi.core.coins.BitcoinMain;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.coins.DogecoinMain;
import com.coinomi.core.coins.LitecoinMain;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;

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
    final String PREFS_NAME = "SharedPrefsFile";
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

//        //If app has never been launched, this code will be executed.
//        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
//        if (settings.getBoolean("firstLaunch", true)) {
//            Log.d("Comments", "First time");
//
//            // First time, run this.
//            System.out.println("Testing... Testing... Testing...");
//
//            //Set the boolean to false to make sure this code never runs again.
//            settings.edit().putBoolean("firstLaunch", false).commit();
//        }
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

    public void onSectionAttached(int number) {
        switch (number) {
            case MENU_BITCOIN:
                mTitle = getString(R.string.bitcoin);
                break;
            case MENU_DOGECOIN:
            default:
                mTitle = getString(R.string.dogecoin);
                break;
            case MENU_LITECOIN:
                mTitle = getString(R.string.litecoin);
                break;
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
        } else if (id == R.id.action_refresh_wallet) {
            refreshWallet();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

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

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_wallet, container, false);
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((WalletActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
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
