package com.coinomi.wallet.ui;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.coinomi.core.coins.CoinID;
import com.coinomi.core.coins.CoinType;
import com.coinomi.core.wallet.WalletPocketHD;
import com.coinomi.wallet.AddressBookProvider;
import com.coinomi.wallet.Constants;
import com.coinomi.wallet.R;
import com.coinomi.wallet.WalletApplication;
import com.coinomi.wallet.util.ThrottlingWalletChangeListener;

import org.bitcoinj.core.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.coinomi.core.Preconditions.checkNotNull;

/**
 *
 */
public class PreviousAddressesFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(PreviousAddressesFragment.class);

    private static final int UPDATE_VIEW = 0;

    private Listener listener;

    private CoinType type;
    private WalletPocketHD pocket;
    private AddressesListAdapter adapter;
    private ContentResolver resolver;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_VIEW:
                    updateView();
            }
        }
    };

    private final ContentObserver addressBookObserver = new ContentObserver(handler) {
        @Override
        public void onChange(final boolean selfChange) {
            adapter.clearLabelCache();
        }
    };

    public static PreviousAddressesFragment newInstance(CoinType coinType) {
        PreviousAddressesFragment fragment = new PreviousAddressesFragment();
        Bundle args = new Bundle();
        args.putString(Constants.ARG_COIN_ID, coinType.getId());
        fragment.setArguments(args);
        return fragment;
    }
    public PreviousAddressesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            type = CoinID.typeFromId(getArguments().getString(Constants.ARG_COIN_ID));
        }
        WalletApplication walletApplication = (WalletApplication) getActivity().getApplication();
        pocket = checkNotNull(walletApplication.getWalletPocket(type));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_previous_addresses, container, false);

        final ListView previousAddresses = (ListView) view.findViewById(R.id.previous_addresses);

        // Set a space to the beginning and end of the list. If possible find a better way
        View spacerView = new View(getActivity());
        spacerView.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.half_standard_margin));
        previousAddresses.addHeaderView(spacerView);
        spacerView = new View(getActivity());
        spacerView.setMinimumHeight(getResources().getDimensionPixelSize(R.dimen.half_standard_margin));
        previousAddresses.addFooterView(spacerView);

        // Init list adapter
        adapter = new AddressesListAdapter(inflater.getContext(), pocket);
        previousAddresses.setAdapter(adapter);

        // Start TransactionDetailsActivity on click
        previousAddresses.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= previousAddresses.getHeaderViewsCount()) {
                    Object obj = parent.getItemAtPosition(position);

                    if (obj != null && obj instanceof Address) {
                        Bundle args = new Bundle();
                        args.putString(Constants.ARG_COIN_ID, type.getId());
                        args.putString(Constants.ARG_ADDRESS, obj.toString());
                        listener.onAddressSelected(args);
                    } else {
                        Toast.makeText(getActivity(), R.string.error_generic, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });


        // Subscribe and update the amount
        pocket.addEventListener(walletListener);

        updateView();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        resolver.registerContentObserver(AddressBookProvider.contentUri(
                getActivity().getPackageName(), type), true, addressBookObserver);
        updateView();
    }

    @Override
    public void onPause() {
        resolver.unregisterContentObserver(addressBookObserver);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        pocket.removeEventListener(walletListener);
        walletListener.removeCallbacks();
        super.onDestroyView();
    }

    private void updateView() {
        adapter.replace(pocket.getIssuedReceiveAddresses(), pocket.getUsedAddresses());
    }

    private final ThrottlingWalletChangeListener walletListener = new ThrottlingWalletChangeListener() {
        @Override
        public void onThrottledWalletChanged() {
            handler.sendMessage(handler.obtainMessage(UPDATE_VIEW));
        }
    };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (Listener) activity;
            resolver = activity.getContentResolver();
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement " + PreviousAddressesFragment.Listener.class);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    public interface Listener {
        public void onAddressSelected(Bundle args);
    }
}
