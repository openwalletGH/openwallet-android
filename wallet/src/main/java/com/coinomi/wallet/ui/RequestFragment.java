package com.coinomi.wallet.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
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

import com.coinomi.core.coins.CoinType;
import com.coinomi.core.uri.CoinURI;
import com.coinomi.core.wallet.WalletPocket;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.GenericUtils;
import com.coinomi.wallet.util.Qr;
import com.coinomi.wallet.util.ShareActionProvider;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Coin;

import javax.annotation.Nullable;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 *
 */
public class RequestFragment extends Fragment {
    private static final String COIN_TYPE = "coin_type";

    private CoinType coinType;
    private TextView addressView;
    private ImageView qrView;

    private Address receiveAddress;
    private Coin amount;
    private String label;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    @Nullable private ShareActionProvider mShareActionProvider;
    private WalletPocket pocket;
    private int maxQrSize;

    public static RequestFragment newInstance(CoinType coinType) {
        RequestFragment fragment = new RequestFragment();
        Bundle args = new Bundle();
        args.putSerializable(COIN_TYPE, coinType);
        fragment.setArguments(args);
        return fragment;
    }
    public RequestFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            coinType = (CoinType) checkNotNull(getArguments().getSerializable(COIN_TYPE));
        }
        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();
        pocket = checkNotNull(walletApplication.getWalletPocket(coinType));
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

        updateView();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            inflater.inflate(R.menu.request, menu);

            // Set up ShareActionProvider's default share intent
            MenuItem shareItem = menu.findItem(R.id.action_share);
            mShareActionProvider = (ShareActionProvider)
                    MenuItemCompat.getActionProvider(shareItem);

            setShareIntent(receiveAddress);
        }
    }

    private void updateView() {
        receiveAddress = pocket.getReceiveAddress();

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
}
