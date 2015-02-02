package com.coinomi.wallet.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.util.GenericUtils;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.core.wallet.exceptions.Bip44KeyLookAheadExceededException;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.Qr;
import com.coinomi.wallet.util.ShareActionProvider;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 *
 */
public class RequestFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(RequestFragment.class);

    private static final int UPDATE_VIEW = 0;

    private CoinType type;
    private TextView addressView;
    private ImageView qrView;
    private View previousAddressesLink;

    private Address receiveAddress;
    private Coin amount;
    private String label;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    @Nullable private ShareActionProvider mShareActionProvider;
    private WalletPocket pocket;
    private int maxQrSize;
    @Nullable private Address showAddress;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    updateView();
            }
        }
    };

    public static RequestFragment newInstance(Bundle args) {
        RequestFragment fragment = new RequestFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public static RequestFragment newInstance(CoinType coinType) {
        return newInstance(coinType, null);
    }

    public static RequestFragment newInstance(CoinType coinType, @Nullable Address showAddress) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_COIN_ID, coinType.getId());
        if (showAddress != null) {
            args.putString(Constants.ARG_ADDRESS, showAddress.toString());
        }
        return newInstance(args);
    }
    public RequestFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            type = CoinID.typeFromId(args.getString(Constants.ARG_COIN_ID));
            if (args.containsKey(Constants.ARG_ADDRESS)) {
                try {
                    showAddress = new Address(type, args.getString(Constants.ARG_ADDRESS));
                } catch (AddressFormatException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();
        pocket = checkNotNull(walletApplication.getWalletPocket(type));
        setHasOptionsMenu(true);
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        // Qr-code size calculation
        int qrPadding = getResources().getDimensionPixelSize(R.dimen.qr_code_padding);
        int qrCodeViewSize = getResources().getDimensionPixelSize(R.dimen.qr_code_size);
        maxQrSize = qrCodeViewSize - 2 * qrPadding;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_request, container, false);

        addressView = (TextView) view.findViewById(R.id.receive_address);
        qrView = (ImageView) view.findViewById(R.id.qr_code);

        previousAddressesLink = view.findViewById(R.id.view_previous_addresses);
        previousAddressesLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), PreviousAddressesActivity.class);
                intent.putExtra(Constants.ARG_COIN_ID, type.getId());
                startActivity(intent);
            }
        });

        updateView();

        pocket.addEventListener(walletListener);

        return view;
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

            // Set up ShareActionProvider's default share intent
            MenuItem shareItem = menu.findItem(R.id.action_share);
            mShareActionProvider = (ShareActionProvider)
                    MenuItemCompat.getActionProvider(shareItem);

            setShareIntent(receiveAddress);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_new_address:
                try {
                    pocket.getFreshReceiveAddress();
                    updateView();
                } catch (Bip44KeyLookAheadExceededException e) {
                    Toast.makeText(getActivity(), R.string.too_many_unused_addresses, Toast.LENGTH_LONG).show();
                }
                return true;
            default:
                // Not one of ours. Perform default menu processing
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateView() {
        receiveAddress = showAddress != null ? showAddress : pocket.getReceiveAddress();

        // Don't show previous addresses link if we are showing a specific address
        if (showAddress == null && pocket.getNumberIssuedReceiveAddresses() != 0) {
            previousAddressesLink.setVisibility(View.VISIBLE);
        } else {
            previousAddressesLink.setVisibility(View.GONE);
        }

        setShareIntent(receiveAddress);

        // TODO, get amount and description, update QR if needed

        addressView.setText(GenericUtils.addressSplitToGroupsMultiline(receiveAddress.toString()));
        // update qr-code
        final String qrContent = CoinURI.convertToCoinURI(receiveAddress, amount, label, null);
        Bitmap qrCodeBitmap = Qr.bitmap(qrContent, maxQrSize);
        qrView.setImageBitmap(qrCodeBitmap);
    }

    private void setShareIntent(Address address) {
        if (mShareActionProvider != null && address != null) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_TEXT, address.toString());
            intent.setType("text/plain");
            mShareActionProvider.setShareIntent(intent);
        }
    }

    private final ThrottlingWalletChangeListener walletListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            handler.sendMessage(handler.obtainMessage(UPDATE_VIEW));
        }
    };
}
