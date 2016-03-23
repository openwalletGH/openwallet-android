package com.coinomi.wallet.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.uri.CoinURIParseException;
import com.coinomi.core.wallet.WalletAccount;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.Keyboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * @author John L. Jegutanis
 */
public class AccountFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(AccountFragment.class);

    private static final int RECEIVE = 0;
    private static final int BALANCE = 1;
    private static final int SEND = 2;

    @Bind(R.id.pager) ViewPager viewPager;
    NavigationDrawerFragment mNavigationDrawerFragment;
    private AppSectionsPagerAdapter pagerAdapter;
    @Nullable private WalletAccount account;
    private Listener listener;
    private WalletApplication application;
    private final Handler handler = new Handler();

    public static AccountFragment getInstance() {
        AccountFragment fragment = new AccountFragment();
        fragment.setArguments(new Bundle());
        return fragment;
    }

    public static AccountFragment getInstance(String accountId) {
        AccountFragment fragment = getInstance();
        fragment.setupArgs(accountId);
        return fragment;
    }

    public void setupArgs(String accountId) {
        getArguments().putString(Constants.ARG_ACCOUNT_ID, accountId);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account, container, false);
        ButterKnife.bind(this, view);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);

        // Set up the ViewPager, attaching the adapter and setting up a listener for when the
        // user swipes between sections.
        // Set OffscreenPageLimit to 2 because receive fragment draws a QR code and we don't
        // want to re-render that if we go to the SendFragment and back
        viewPager.setOffscreenPageLimit(2);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int pos, float posOffset, int posOffsetPixels) { }

            @Override
            public void onPageSelected(int position) {
                if (position == BALANCE) Keyboard.hideKeyboard(getActivity());
                if (listener != null) {
                    switch (position) {
                        case RECEIVE:
                            listener.onReceiveSelected();
                            break;
                        case BALANCE:
                            listener.onBalanceSelected();
                            break;
                        case SEND:
                            listener.onSendSelected();
                            break;
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) { }
        });

        return view;
    }

    @Override
    public void onDestroyView() {
        ButterKnife.unbind(this);
        mNavigationDrawerFragment = null;
        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        applyArguments();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            this.listener = (Listener) activity;
            this.application = (WalletApplication) activity.getApplication();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mNavigationDrawerFragment != null && !mNavigationDrawerFragment.isDrawerOpen() &&
                isVisible() && account != null) {

            switch (viewPager.getCurrentItem()) {
                case RECEIVE:
                    inflater.inflate(R.menu.request, menu);
                    MenuItem newAddressItem = menu.findItem(R.id.action_new_address);
                    if (newAddressItem != null) {
                        newAddressItem.setVisible(account.canCreateNewAddresses());
                    }
                    break;
                case BALANCE:
                    inflater.inflate(R.menu.balance, menu);
                    // Disable sign/verify for coins that don't support it
                    menu.findItem(R.id.action_sign_verify_message)
                            .setVisible(account.getCoinType().canSignVerifyMessages());
                    break;
                case SEND:
                    inflater.inflate(R.menu.send, menu);
                    break;
            }
        }
    }

    public void applyArguments() {
        Bundle args = getArguments();
        if (args != null && args.containsKey(Constants.ARG_ACCOUNT_ID)) {
            String accountId = args.getString(Constants.ARG_ACCOUNT_ID);
            // If no account set or if it was changed
            if (account == null || !account.getId().equals(accountId)) {
                account = application.getAccount(accountId);
                if (account != null) {
                    pagerAdapter = new AppSectionsPagerAdapter(getActivity(), account);
                    viewPager.setAdapter(pagerAdapter);
                    viewPager.setCurrentItem(BALANCE);
                    viewPager.getAdapter().notifyDataSetChanged();
                }
            }
        }
    }

    @Nullable
    public WalletAccount getAccount() {
        return account;
    }

    public void setSendFromCoin(final CoinURI coinUri)
            throws CoinURIParseException{
        if (viewPager != null) {
            viewPager.setCurrentItem(SEND);

            ((SendFragment) pagerAdapter.getItem(SEND)).updateStateFrom(coinUri);
        } else {
            // Should not happen
            Toast.makeText(getActivity(), R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    public boolean goToBalance() {
        if (viewPager != null && viewPager.getCurrentItem() != BALANCE) {
            viewPager.setCurrentItem(BALANCE);
            return true;
        }
        return false;
    }

    public boolean resetSend() {
        if (pagerAdapter != null) {
            ((SendFragment) pagerAdapter.getItem(SEND)).reset();
            return true;
        }
        return false;
    }

    public boolean goToSend() {
        if (viewPager != null && viewPager.getCurrentItem() != SEND) {
            viewPager.setCurrentItem(SEND);
            return true;
        }
        return false;
    }

    private static class AppSectionsPagerAdapter extends FragmentStatePagerAdapter {

        private final FragmentActivity activity;
        private final String accountId;
        private AddressRequestFragment request;
        private SendFragment send;
        private BalanceFragment balance;

        public AppSectionsPagerAdapter(FragmentActivity activity, WalletAccount account) {
            super(activity.getSupportFragmentManager());
            this.activity = activity;
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
                    return activity.getString(R.string.wallet_title_request);
                case SEND:
                    return activity.getString(R.string.wallet_title_send);
                case BALANCE:
                default:
                    return activity.getString(R.string.wallet_title_balance);
            }
        }
    }

    public interface Listener extends BalanceFragment.Listener, SendFragment.Listener {
        // TODO make an external interface so that SendFragment and AddressRequestFragment can use.
        void registerActionMode(ActionMode actionMode);
        void onReceiveSelected();
        void onBalanceSelected();
        void onSendSelected();
    }
}
