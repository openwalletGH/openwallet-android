package com.coinomi.wallet.ui;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.Toast;

import com.coinomi.core.coins.CoinType;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;

/**
 * @author Giannis Dzegoutanis
 * @author Andreas Schildbach
 */
final public class WalletActivity extends ActionBarActivity implements
        NavigationDrawerFragment.NavigationDrawerCallbacks, ActionBar.TabListener {

    private static final int RECEIVE = 0;
    private static final int INFO = 1;
    private static final int SEND = 2;

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
//        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
//            @Override
//            public void onPageSelected(int position) {
//                // When swiping between different app sections, select the corresponding tab.
//                // We can also use ActionBar.Tab#select() to do this if we have a reference to the
//                // Tab.
//                //TODO
////                actionBar.setSelectedNavigationItem(position);
//                Toast.makeText(WalletActivity.this, "Touched " + position, Toast.LENGTH_LONG);
//            }
//        });

        // Hack to make the ViewPager work
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
//        Fragment fragment;
//        if (position == 0) {
//            fragment = InfoFragment.newInstance(DogecoinTest.get());
//        }
//        else if (position == 2) {
//            fragment = WalletSendCoins.newInstance(DogecoinTest.get());
//        }
//        else {
//            fragment = PlaceholderFragment.newInstance(position + 1);
//        }



        if (mViewPager != null) {
            AppSectionsPagerAdapter adapter =
                    new AppSectionsPagerAdapter(this, Constants.COINS_TEST.get(0));
            mViewPager.setAdapter(adapter);
            mViewPager.setCurrentItem(INFO);
        }


        // update the main content by replacing fragments
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 0:
                mTitle = getString(R.string.title_section1);
                break;
            case 1:
                mTitle = getString(R.string.title_section2);
                break;
            case 2:
                mTitle = getString(R.string.title_section3);
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
        }
        return super.onOptionsItemSelected(item);
    }

    private void startIntro() {
        Intent introIntent = new Intent(this, IntroActivity.class);
        startActivity(introIntent);
    }

    private void startRestore() {
        Intent restoreIntent = new Intent(this, IntroActivity.class);
        restoreIntent.putExtra(IntroActivity.RESTORE, true);
        startActivity(restoreIntent);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in the ViewPager.
//        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, android.support.v4.app.FragmentTransaction fragmentTransaction) {

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




    private static class AppSectionsPagerAdapter extends FragmentPagerAdapter {

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
                    return ReceiveFragment.newInstance(type.getName(), type.getSymbol());
                case SEND:
                    return WalletSendCoins.newInstance(type);
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
                    return walletActivity.getString(R.string.wallet_title_receive);
                case SEND:
                    return walletActivity.getString(R.string.wallet_title_send);
                case INFO:
                default:
                    return walletActivity.getString(R.string.wallet_title_info);
            }
        }
    }

}
