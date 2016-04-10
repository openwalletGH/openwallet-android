package com.coinomi.wallet.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

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

import static com.coinomi.wallet.util.UiUtils.toastGenericError;

/**
 * @author John L. Jegutanis
 */
public class AccountFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(AccountFragment.class);

    private static final String ACCOUNT_CURRENT_SCREEN = "account_current_screen";
    private static final int NUM_OF_SCREENS = 3;
    // Set offscreen page limit to 2 because receive fragment draws a QR code and we don't
    // want to re-render that if we go to the SendFragment and back
    private static final int OFF_SCREEN_LIMIT = 2;

    private static final int RECEIVE = 0;
    private static final int BALANCE = 1;
    private static final int SEND = 2;

    private int currentScreen;
    @Bind(R.id.pager) ViewPager viewPager;
    NavigationDrawerFragment mNavigationDrawerFragment;
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
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_account, container, false);
        ButterKnife.bind(this, view);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);

        viewPager.setOffscreenPageLimit(OFF_SCREEN_LIMIT);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int pos, float posOffset, int posOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                currentScreen = position;
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
                        default:
                            throw new RuntimeException("Unknown screen item: " + position);
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
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
    public void onAttach(final Context context) {
        super.onAttach(context);
        try {
            this.listener = (Listener) context;
            this.application = (WalletApplication) context.getApplicationContext();
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement " + Listener.class);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(ACCOUNT_CURRENT_SCREEN, currentScreen);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            currentScreen = savedInstanceState.getInt(ACCOUNT_CURRENT_SCREEN, BALANCE);
            updateView();
        }
        super.onViewStateRestored(savedInstanceState);
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

    private void updateView() {
        goToItem(currentScreen, true);
    }

    public void applyArguments() {
        Bundle args = getArguments();
        if (args != null && args.containsKey(Constants.ARG_ACCOUNT_ID)) {
            String accountId = args.getString(Constants.ARG_ACCOUNT_ID);
            // If no account set or if it was changed
            if (account == null || !account.getId().equals(accountId) ||
                    mustUpdatePagerAdapter(viewPager.getAdapter())) {
                account = application.getAccount(accountId);
                resetPagerAdapter();
            }
        }
    }

    private void resetPagerAdapter() {
        if (account != null) {
            PagerAdapter adapter = new AppSectionsPagerAdapter(getActivity(),
                    getFragmentManager(), account);
            viewPager.setAdapter(adapter);
            adapter.notifyDataSetChanged();
        }
    }

    private boolean mustUpdatePagerAdapter(PagerAdapter adapter) {
        if (adapter instanceof AppSectionsPagerAdapter) {
            // If the fragment manager changed, must update the adapter
            if (((AppSectionsPagerAdapter) adapter).fm == getFragmentManager()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public WalletAccount getAccount() {
        return account;
    }

    public void setSendFromCoin(final CoinURI coinUri)
            throws CoinURIParseException{
        if (viewPager != null) {
            viewPager.setCurrentItem(SEND);
            SendFragment f = getSendFragment();
            if (f != null) {
                f.updateStateFrom(coinUri);
            } else {
                log.warn("Expected fragment to be not null");
                toastGenericError(getContext());
            }
        } else {
            // Should not happen
            toastGenericError(getContext());
        }
    }

    @Nullable
    private SendFragment getSendFragment() {
        return (SendFragment) getFragment(getFragmentManager(), SEND);
    }

    @Nullable
    private static Fragment getFragment(FragmentManager fm, int item) {
        for (Fragment f : fm.getFragments()) {
            switch (item) {
                case RECEIVE:
                    if (f instanceof AddressRequestFragment) return f;
                    break;
                case BALANCE:
                    if (f instanceof BalanceFragment) return f;
                    break;
                case SEND:
                    if (f instanceof SendFragment) return f;
                    break;
                default:
                    throw new RuntimeException("Cannot get fragment, unknown screen item: " + item);
            }
        }
        return null;
    }


    private static Fragment getFragmentOrCreate(FragmentManager fm, WalletAccount account, int item) {
        Fragment f = getFragment(fm, item);

        if (f == null) {
            f = createFragment(account, item);
        }

        return f;
    }

    private static Fragment createFragment(WalletAccount account, int item) {
        String accountId = account.getId();
        switch (item) {
            case RECEIVE:
                return AddressRequestFragment.newInstance(accountId);
            case BALANCE:
                return BalanceFragment.newInstance(accountId);
            case SEND:
                return SendFragment.newInstance(accountId);
            default:
                throw new RuntimeException("Cannot create fragment, unknown screen item: " + item);
        }
    }

    public boolean goToReceive(boolean smoothScroll) {
        return goToItem(RECEIVE, smoothScroll);
    }

    public boolean goToBalance(boolean smoothScroll) {
        return goToItem(BALANCE, smoothScroll);
    }

    public boolean goToSend(boolean smoothScroll) {
        return goToItem(SEND, smoothScroll);
    }

    private boolean goToItem(int item, boolean smoothScroll) {
        if (viewPager != null && viewPager.getCurrentItem() != item) {
            viewPager.setCurrentItem(item, smoothScroll);
            return true;
        }
        return false;
    }

    public boolean resetSend() {
        SendFragment f = getSendFragment();
        if (f != null) {
            f.reset();
            return true;
        }
        return false;
    }

    private static class AppSectionsPagerAdapter extends FragmentStatePagerAdapter {
        final Context context;
        final FragmentManager fm;
        private final WalletAccount account;

        public AppSectionsPagerAdapter(Context context, FragmentManager fm, WalletAccount account) {
            super(fm);
            this.context = context;
            this.fm = fm;
            this.account = account;
        }

        @Override
        public Fragment getItem(int i) {
            switch (i) {
                case RECEIVE:
                case SEND:
                case BALANCE:
                    return getFragmentOrCreate(fm, account, i);
                default:
                    throw new RuntimeException("Cannot get item, unknown screen item: " + i);
            }
        }


        @Override
        public int getCount() {
            return NUM_OF_SCREENS;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case RECEIVE:
                    return context.getString(R.string.wallet_title_request);
                case SEND:
                    return context.getString(R.string.wallet_title_send);
                case BALANCE:
                default:
                    return context.getString(R.string.wallet_title_balance);
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
